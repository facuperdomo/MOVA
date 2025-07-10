package com.movauy.mova.service.account;

import com.movauy.mova.dto.AccountCreateDTO;
import com.movauy.mova.dto.AccountItemDTO;
import com.movauy.mova.dto.AccountItemPaymentDTO;
import com.movauy.mova.dto.ItemPaymentInfoDTO;
import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.PayItemsRequestDTO;
import com.movauy.mova.dto.PaymentRequestDTO;
import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SplitStatusDTO;
import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.account.AccountItem;
import com.movauy.mova.model.account.PaymentAccount;
import com.movauy.mova.model.account.PaymentAccount.Status;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.account.AccountItemRepository;
import com.movauy.mova.repository.account.AccountRepository;
import com.movauy.mova.repository.account.PaymentAccountRepository;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.ingredient.IngredientRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.finance.CashBoxService;
import com.movauy.mova.service.finance.CashRegisterService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@RequiredArgsConstructor
public class AccountService {

    @Autowired
    private AccountItemRepository accountItemRepository;
    private final AccountRepository accountRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashBoxService cashBoxService;
    private final CashRegisterService cashRegisterService;
    private final AuthService authService;
    private final IngredientRepository ingredientRepository;
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public Account createAccount(AccountCreateDTO dto) {
        Branch branch = branchRepository.findById(dto.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Sucursal no encontrada"));

        Account account = new Account();
        account.setName(dto.getName());
        account.setBranch(branch);
        // Por defecto, closed == false y splitTotal == null
        return accountRepository.save(account);
    }

    public List<Account> getAccountsByBranch(Long branchId, boolean closed) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Sucursal no encontrada"));
        return accountRepository.findByBranchAndClosed(branch, closed);
    }

    /**
     * Añade ítems a la cuenta y, si hay un split activo, reinicia
     * splitRemaining.
     */
    @Transactional
    public Account addItemToAccount(Long accountId, AccountItemDTO dto) {
        // 1) Cargo la cuenta CON sus items e ingredients
        Account account = accountRepository
                .findByIdWithItemsAndIngredients(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        // 2) Cargo el producto y la lista de ingredientes
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Producto no encontrado"));
        List<Ingredient> ingredientList = ingredientRepository.findAllById(dto.getIngredientIds());
// ...y los conviertes a Set
        Set<Ingredient> ingredients = new HashSet<>(ingredientList);
        // 3) Por cada unidad pedida, creo UNA línea independiente con quantity = 1
        for (int i = 0; i < dto.getQuantity(); i++) {
            AccountItem single = new AccountItem();
            single.setAccount(account);
            single.setProduct(product);
            single.setQuantity(1);
            single.setUnitPrice(product.getPrice());
            single.setPaid(false);
            single.setIngredients(ingredients);
            accountItemRepository.save(single);
        }

        // 4) Si hay un split activo, reinícialo
        if (account.getSplitTotal() != null) {
            initOrUpdateSplit(accountId, account.getSplitTotal());
        }

        // 5) Recargo la cuenta con sus líneas + ingredientes y devuelvo
        return accountRepository
                .findByIdWithItemsAndIngredients(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }

    public Account getById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }

    public void deleteAccount(Long id) {
        Account account = getById(id);
        accountRepository.delete(account);
    }

    /**
     * Cierra la cuenta: genera una Sale asociada a la caja especificada por
     * código.
     *
     * @param accountId ID de la cuenta a cerrar.
     * @param rawToken Header "Authorization" completo (p.ej. "Bearer ...").
     * @param code Código de la caja donde se registrará la venta.
     * @return DTO de la nueva venta.
     */
    @Transactional
    public SaleDTO closeAccount(Long accountId, String rawToken, String code) {
        // 1) Buscamos la cuenta y validamos estado
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada: " + accountId));
        if (account.isClosed()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La cuenta ya está cerrada");
        }

        // 2) Validamos el código y obtenemos la caja abierta para ese code
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El parámetro 'code' es obligatorio");
        }
        String token = rawToken.replace("Bearer ", "");
        CashBox box = cashBoxService.getOpenCashBox(token, code);

        // 3) Extraemos usuario autenticado
        User usuarioActual = authService.getUserEntityFromToken(rawToken);
        if (usuarioActual == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Usuario no válido");
        }

        // 4) Armamos la Sale
        Sale sale = new Sale();
        sale.setBranch(account.getBranch());
        sale.setAccount(account);
        sale.setEstado(Sale.EstadoVenta.ACTIVA);
        sale.setDateTime(LocalDateTime.now());
        sale.setCashBox(box);
        sale.setUser(usuarioActual);

        // 5) Convertimos cada AccountItem en SaleItem
        sale.setItems(new ArrayList<>());
        for (AccountItem item : account.getItems()) {
            SaleItem si = new SaleItem();
            si.setSale(sale);
            si.setProduct(item.getProduct());
            si.setQuantity(item.getQuantity());
            si.setUnitPrice(item.getUnitPrice());
            sale.getItems().add(si);
        }

        // 6) Calculamos y seteamos totalAmount
        double total = sale.getItems().stream()
                .mapToDouble(i -> i.getUnitPrice() * i.getQuantity())
                .sum();
        sale.setTotalAmount(total);

        // 7) Persistimos la venta y cerramos la cuenta
        saleRepository.save(sale);
        account.setClosed(true);
        accountRepository.save(account);

        // 8) Mapeamos a DTO
        SaleDTO dto = new SaleDTO();
        dto.setId(sale.getId());
        dto.setItems(mapSaleItemsToDTOs(sale.getItems()));
        dto.setTotalAmount(BigDecimal.valueOf(total));
        dto.setPaymentMethod("CUENTA");
        dto.setDateTime(sale.getDateTime());
        return dto;
    }

    private List<SaleItemDTO> mapSaleItemsToDTOs(List<SaleItem> items) {
        List<SaleItemDTO> list = new ArrayList<>();
        for (SaleItem item : items) {
            SaleItemDTO dto = new SaleItemDTO();
            dto.setProductId(item.getProduct().getId());
            dto.setQuantity(item.getQuantity());
            dto.setUnitPrice(item.getUnitPrice());
            list.add(dto);
        }
        return list;
    }

    /**
     * Registra un pago en la cuenta. Si closeAfter == true y el pago cubre el
     * saldo restante, la cuenta se cierra automáticamente.
     *
     * @param accountId id de la cuenta a la que se le hace el pago
     * @param amount monto que se está pagando en esta operación
     * @param payerName nombre del pagador (puede ser opcional)
     * @param closeAfter true si, tras registrar el pago, debe cerrarse la
     * cuenta
     */
    public Account registerPayment(Long accountId, BigDecimal amount, String payerName, Boolean closeAfter) {
        Account account = getById(accountId);

        if (account.isClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta ya está cerrada");
        }

        // 1) Calculamos cuánto se ha pagado hasta ahora
        BigDecimal totalPaidSoFar = paymentAccountRepository
                .findByAccountId(accountId)
                .stream()
                .map(PaymentAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2) Calculamos el total actual de la cuenta
        BigDecimal accountTotal = account.calculateTotal();

        // 3) Calculamos el remaining para saber si cubre todo
        BigDecimal remaining = accountTotal.subtract(totalPaidSoFar).subtract(amount);

        // 4) Creamos el PaymentAccount y seteamos el status según corresponda
        PaymentAccount payment = new PaymentAccount();
        payment.setAccount(account);
        payment.setAmount(amount);
        payment.setPayerName(payerName != null ? payerName : "–");
        payment.setPaidAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(closeAfter) && remaining.compareTo(BigDecimal.ZERO) <= 0) {
            payment.setStatus(Status.PAID_IN_FULL);
        } else {
            payment.setStatus(Status.PARTIALLY_PAID);
        }
        paymentAccountRepository.save(payment);

        if (account.getSplitTotal() != null && account.getSplitRemaining() != null && account.getSplitRemaining() > 0) {
            account.setSplitRemaining(account.getSplitRemaining() - 1);
        }

        // 5) Si closeAfter y cubrimos el total, cerramos la cuenta
        if (Boolean.TRUE.equals(closeAfter) && remaining.compareTo(BigDecimal.ZERO) <= 0) {
            Sale sale = new Sale();
            sale.setBranch(account.getBranch());
            sale.setEstado(Sale.EstadoVenta.ACTIVA);
            sale.setDateTime(LocalDateTime.now());
            for (AccountItem item : account.getItems()) {
                SaleItem si = new SaleItem();
                si.setSale(sale);
                si.setProduct(item.getProduct());
                si.setQuantity(item.getQuantity());
                si.setUnitPrice(item.getUnitPrice());
                sale.getItems().add(si);
            }
            saleRepository.save(sale);
            account.setClosed(true);
            accountRepository.save(account);
        }

        return accountRepository.save(account);
    }

    /**
     * Elimina un ítem de la cuenta y, si hay un split activo, reinicia
     * splitRemaining.
     */
    @Transactional
    public void removeItemFromAccount(Long accountId, Long itemId) {
        Account account = getById(accountId);

        // Buscamos el ítem dentro de la cuenta
        AccountItem item = account.getItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("❓ Ítem {} no encontrado en la cuenta {}", itemId, accountId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Ítem no encontrado en la cuenta: " + itemId
                    );
                });

        // Logueamos el estado de 'paid' antes de intentar borrarlo
        log.debug("🔒 Intentando borrar ítem {} de la cuenta {} (paid={})",
                itemId, accountId, item.isPaid());

        if (item.isPaid()) {
            log.warn("⛔ Bloqueando eliminación de ítem {} porque ya está marcado como pagado", itemId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No puedes eliminar un ítem que ya fue pagado"
            );
        }

        // Si no está pagado, lo eliminamos
        account.getItems().remove(item);
        accountRepository.save(account);

        // Reiniciamos el split si existía
        if (account.getSplitTotal() != null) {
            log.debug("♻️ Reiniciando splitRemaining para la cuenta {} tras eliminar ítem",
                    accountId);
            initOrUpdateSplit(accountId, account.getSplitTotal());
        }

        log.debug("✅ Ítem {} eliminado correctamente de la cuenta {}", itemId, accountId);
    }

    /**
     * Actualiza la cantidad de un ítem y, si hay un split activo, reinicia
     * splitRemaining.
     */
    @Transactional
    public Account updateItemQuantity(Long accountId, Long itemId, int newQty) {
        Account account = getById(accountId);

        AccountItem mainItem = account.getItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Item de cuenta no encontrado: " + itemId));

        // elimino las otras líneas del mismo producto
        Long prodId = mainItem.getProduct().getId();
        account.getItems().removeIf(it
                -> !it.getId().equals(itemId)
                && it.getProduct().getId().equals(prodId)
        );

        // ajusto cantidad
        mainItem.setQuantity(newQty);
        Account saved = accountRepository.save(account);

        if (saved.getSplitTotal() != null) {
            initOrUpdateSplit(accountId, saved.getSplitTotal());
            saved = accountRepository.findById(accountId).get();
        }

        return saved;
    }

    /**
     * Inicializa o actualiza el split definiendo splitTotal = people y
     * splitRemaining = people
     */
    @Transactional
    public Account initOrUpdateSplit(Long accountId, int people) {
        Account acct = getById(accountId);
        acct.setSplitTotal(people);
        acct.setSplitRemaining(people);
        return accountRepository.save(acct);
    }

    @Transactional(readOnly = true)
    public SplitStatusDTO getSplitStatus(Long accountId) {
        // 1) Cargo la cuenta
        Account acct = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada: " + accountId));

        // 2) Calculo el split leyendo splitTotal y splitRemaining del propio account
        int totalSlices = acct.getSplitTotal() == null ? 0 : acct.getSplitTotal();
        int remainingSlices = acct.getSplitRemaining() == null
                ? totalSlices
                : acct.getSplitRemaining();
        int paidCount = totalSlices - remainingSlices;

        // 3) Calculo totales en dinero
        BigDecimal currentTotal = acct.calculateTotal();
        BigDecimal paidMoney = paymentAccountRepository.findByAccountId(accountId)
                .stream()
                .map(PaymentAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("🔍 getSplitStatus(account={}): totalSlices={} paidCount={} remainingSlices={}",
                accountId, totalSlices, paidCount, remainingSlices);
        log.debug("    currentTotal={}  paidMoney={}", currentTotal, paidMoney);

        // 4) Distribuyo el paidMoney línea a línea (para el detalle por ítem)
        List<AccountItem> items = acct.getItems();
        BigDecimal leftover = paidMoney;
        Map<Long, Integer> paidQtyMap = items.stream()
                .collect(Collectors.toMap(
                        AccountItem::getId,
                        it -> 0
                ));

        for (AccountItem it : items) {
            if (leftover.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal unitPrice = BigDecimal.valueOf(it.getUnitPrice());
            int qtyTotal = it.getQuantity();
            BigDecimal rowTotal = unitPrice.multiply(BigDecimal.valueOf(qtyTotal));

            if (leftover.compareTo(rowTotal) >= 0) {
                // pago toda la línea
                paidQtyMap.put(it.getId(), qtyTotal);
                leftover = leftover.subtract(rowTotal);
            } else {
                // sólo algunas unidades
                int paidUnits = leftover.divide(unitPrice, 0, RoundingMode.FLOOR).intValue();
                paidQtyMap.put(it.getId(), paidUnits);
                leftover = BigDecimal.ZERO;
            }
            log.debug("    Item[{}] qty={} -> paidUnits={}  (leftover={})",
                    it.getId(), qtyTotal, paidQtyMap.get(it.getId()), leftover);
        }

        // 5) Cálculo de share: lo que queda dividido entre las porciones restantes
        BigDecimal remainingMoney = currentTotal.subtract(paidMoney);
        BigDecimal share = remainingSlices > 0
                ? remainingMoney.divide(
                        BigDecimal.valueOf(remainingSlices),
                        2,
                        RoundingMode.HALF_UP
                )
                : BigDecimal.ZERO;

        // 6) Armo la lista de DTOs de detalle por ítem (incluye parciales)
        List<ItemPaymentInfoDTO> infoByItem = items.stream()
                .map(it -> {
                    int paidQty = paidQtyMap.get(it.getId());
                    return new ItemPaymentInfoDTO(it.getId(), it.getQuantity(), paidQty);
                })
                .filter(dto -> dto.getPaidQuantity() > 0)
                .collect(Collectors.toList());

        log.debug("    InfoByItem (>=1 unidad): {}", infoByItem);

        // 7) Devuelvo el DTO completo
        return SplitStatusDTO.of(
                totalSlices,
                remainingSlices,
                paidMoney,
                currentTotal,
                share,
                infoByItem
        );
    }

    /**
     * Devuelve, para cada AccountItem de la cuenta, cuántas unidades están
     * pagadas y un flag "paid" si ya cubre toda la línea.
     */
    public List<AccountItemPaymentDTO> getAccountItemsWithPaymentInfo(Long accountId) {
        Account acct = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        List<AccountItemPaymentDTO> dtoList = new ArrayList<>();

        for (AccountItem it : acct.getItems()) {
            // Suponemos que en AccountItem ya agregaste el booleano `paid` 
            // cuando se creó el modelo:
            boolean fullyPaid = it.isPaid();

            // Si quieres permitir pagos parciales dentro de una línea (por ej. 2 de 3 unidades),
            // podrías calcular paidQty de manera más fina. Con lo que hicimos en addPaymentForItems,
            // solemos marcar toda la línea de golpe, así que:
            int paidUnits = fullyPaid ? it.getQuantity() : 0;

            AccountItemPaymentDTO dto = new AccountItemPaymentDTO(
                    it.getId(), // itemId
                    it.getProduct().getId(), // productId
                    it.getProduct().getName(), // productName
                    it.getQuantity(), // quantity
                    BigDecimal.valueOf(it.getUnitPrice()), // unitPrice
                    paidUnits, // paidQty
                    fullyPaid // paid
            );
            dtoList.add(dto);
        }

        return dtoList;
    }

    /**
     * 1) Marca como pagado (paid=true) cada AccountItem cuyo ID venga en la
     * lista. 2) Suma (unitPrice * quantity) de todos esos ítems para crear un
     * único PaymentAccount.
     */
    @Transactional
    public void payItemsAndRecordPayment(Long accountId, List<Long> itemIds, String payerName) {
        // 1) Buscamos la cuenta con todos sus ítems
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cuenta no encontrada: " + accountId
        ));

        // 2) Contamos cuántas veces aparece cada itemId
        Map<Long, Long> ocurrencias = itemIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        BigDecimal totalAmountToPay = BigDecimal.ZERO;

        // 3) Procesamos cada línea
        for (Map.Entry<Long, Long> entry : ocurrencias.entrySet()) {
            Long itemId = entry.getKey();
            Long unidadesAPagar = entry.getValue();

            // 3.a) Localizamos el AccountItem en la cuenta
            AccountItem originalItem = account.getItems().stream()
                    .filter(ai -> ai.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El ítem " + itemId + " no existe en la cuenta " + accountId
            ));

            int cantidadOriginal = originalItem.getQuantity();
            BigDecimal precioUnitario = BigDecimal.valueOf(originalItem.getUnitPrice());

            if (unidadesAPagar < cantidadOriginal) {
                // — Caso A: Pago parcial de esta línea —

                // 1) Reduzco la cantidad de la línea original
                originalItem.setQuantity(cantidadOriginal - unidadesAPagar.intValue());
                originalItem.setPaid(false);

                // 2) Creo una nueva línea ya marcada como pagada
                AccountItem lineaNuevaPagada = AccountItem.builder()
                        .account(account)
                        .product(originalItem.getProduct())
                        .quantity(unidadesAPagar.intValue())
                        .unitPrice(originalItem.getUnitPrice())
                        .paid(true)
                        .build();

                account.getItems().add(lineaNuevaPagada);

                // 3) Acumulo el subtotal
                BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(unidadesAPagar));
                totalAmountToPay = totalAmountToPay.add(subtotal);

            } else {
                // — Caso B: Pago de toda la línea —

                originalItem.setPaid(true);

                BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidadOriginal));
                totalAmountToPay = totalAmountToPay.add(subtotal);
            }
        }

        // 4) Registro el pago global en PaymentAccount
        PaymentAccount pago = new PaymentAccount();
        pago.setAccount(account);
        pago.setAmount(totalAmountToPay);
        pago.setPayerName((payerName == null || payerName.isBlank()) ? "–" : payerName);
        pago.setPaidAt(LocalDateTime.now());
        pago.setStatus(Status.PARTIALLY_PAID); // o PAID_IN_FULL según tu lógica

        paymentAccountRepository.save(pago);

        // 5) Persistimos cambios en la cuenta y sus ítems
        accountRepository.save(account);
    }

    /**
     * Paga ítems sueltos y construye un OrderDTO sólo con esos ítems.
     */
    public OrderDTO payItemsAndBuildReceipt(Long accountId, PayItemsRequestDTO req) {
        log.info("▶ payItemsAndBuildReceipt START: accountId={}, request={}", accountId, req);

        // 1) Registra el pago y marca los items como paid
        payItemsAndRecordPayment(accountId, req.getItemIds(), req.getPayerName());
        log.debug("   → payItemsAndRecordPayment completed for itemIds={}", req.getItemIds());

        // 2) Calcula cuántas unidades de cada itemId se pagaron
        Map<Long, Long> occurrences = req.getItemIds().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        log.debug("   → occurrences map: {}", occurrences);

        // 3) Obtengo la cuenta para sacar sucursal y los accountItems
        Account account = getById(accountId);
        Branch branch = account.getBranch();
        log.debug("   → Loaded Account {} with branch {}", accountId, branch.getName());

        // 4) Construyo la lista de SaleItemDTO que acabo de pagar
        List<SaleItemDTO> paidItems = occurrences.entrySet().stream()
                .map(e -> {
                    Long itemId = e.getKey();
                    int qty = e.getValue().intValue();
                    AccountItem it = account.getItems().stream()
                            .filter(ai -> ai.getId().equals(itemId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Item no encontrado tras pago: " + itemId));
                    SaleItemDTO dto = new SaleItemDTO();
                    dto.setProductId(it.getProduct().getId());
                    dto.setQuantity(qty);
                    dto.setUnitPrice(it.getUnitPrice());
                    return dto;
                })
                .toList();
        log.debug("   → paidItems DTOs: {}", paidItems);

        double totalPaid = paidItems.stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();
        log.info("   → totalPaid computed: {}", totalPaid);

        OrderDTO result = OrderDTO.builder()
                .id(accountId)
                .totalAmount(totalPaid)
                .dateTime(LocalDateTime.now().toString())
                .paymentMethod("CUENTA")
                .items(paidItems)
                .branchName(branch.getName())
                .branchRut(branch.getRut())
                .branchAddress(branch.getLocation())
                .companyName(branch.getCompany().getName())
                .build();

        log.info("◀ payItemsAndBuildReceipt END: generated OrderDTO={}", result);
        return result;
    }

    /**
     * Registra un pago parcial (split) y construye un OrderDTO con TODOS los
     * ítems.
     */
    public OrderDTO registerPaymentAndBuildReceipt(Long accountId, PaymentRequestDTO req) {
        log.info("▶ registerPaymentAndBuildReceipt START: accountId={}, request={}", accountId, req);

        // 1) Registro el pago (sin cerrar la cuenta)
        Account account = registerPayment(accountId, req.getAmount(), req.getPayerName(), false);
        log.debug("   → registerPayment completed, account splitRemaining={}", account.getSplitRemaining());

        Branch branch = account.getBranch();
        log.debug("   → Loaded branch for partial payment: {}", branch.getName());

        // 2) Recojo todos los ítems de la cuenta para el recibo
        List<SaleItemDTO> allItems = account.getItems().stream()
                .map(ai -> {
                    SaleItemDTO dto = new SaleItemDTO();
                    dto.setProductId(ai.getProduct().getId());
                    dto.setQuantity(ai.getQuantity());
                    dto.setUnitPrice(ai.getUnitPrice());
                    return dto;
                })
                .toList();
        log.debug("   → allItems DTOs for partial receipt: {}", allItems);

        OrderDTO result = OrderDTO.builder()
                .id(accountId)
                .totalAmount(req.getAmount().doubleValue())
                .dateTime(LocalDateTime.now().toString())
                .paymentMethod("CUENTA")
                .items(allItems)
                .branchName(branch.getName())
                .branchRut(branch.getRut())
                .branchAddress(branch.getLocation())
                .companyName(branch.getCompany().getName())
                .build();

        log.info("◀ registerPaymentAndBuildReceipt END: generated OrderDTO={}", result);
        return result;
    }

    /**
     * Cierra la cuenta por completo y construye un OrderDTO de cierre.
     */
    @Transactional
    public OrderDTO closeAccountAndBuildReceipt(
            Long accountId,
            String rawToken,
            String code,
            PaymentRequestDTO req
    ) {
        log.info("▶ closeAccountAndBuildReceipt START: accountId={} code={}", accountId, code);

        // 1) Invoco la lógica de cierre normal que devuelve un SaleDTO usando el código
        SaleDTO sale = closeAccount(accountId, rawToken, code);
        log.debug("   → closeAccount returned SaleDTO id={} total={}",
                sale.getId(), sale.getTotalAmount());

        // 2) Recupero la cuenta cerrada para extraer sucursal y compañía
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada: " + accountId));
        Branch branch = account.getBranch();

        // 3) Convierto directamente los SaleItemDTO que trae el SaleDTO
        List<SaleItemDTO> items = sale.getItems();

        // 4) Armo el OrderDTO final
        OrderDTO result = OrderDTO.builder()
                .id(sale.getId())
                .totalAmount(sale.getTotalAmount().doubleValue())
                .dateTime(sale.getDateTime().toString())
                .paymentMethod(req.getPaymentMethod())
                .items(items)
                .branchName(branch.getName())
                .branchRut(branch.getRut())
                .branchAddress(branch.getLocation())
                .companyName(branch.getCompany().getName())
                .build();

        log.info("◀ closeAccountAndBuildReceipt END: generated OrderDTO={}", result);
        return result;
    }

    public List<AccountItem> getItemsByAccountId(Long accountId) {
        return accountItemRepository.findByAccountId(accountId);
    }

    private boolean sameIngredientSet(AccountItem it, List<Long> incomingIds) {
        List<Long> existingIds = it.getIngredients().stream()
                .map(Ingredient::getId)
                .sorted()
                .toList();
        List<Long> newIds = new ArrayList<>(incomingIds);
        Collections.sort(newIds);
        return existingIds.equals(newIds);
    }
}
