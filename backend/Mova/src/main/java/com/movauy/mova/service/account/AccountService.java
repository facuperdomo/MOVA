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
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.account.AccountRepository;
import com.movauy.mova.repository.account.PaymentAccountRepository;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterService cashRegisterService;
    private final AuthService authService;
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
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // 1) Si ya había una línea sin pagar, apilo ahí, si no la creo
        AccountItem existing = account.getItems().stream()
                .filter(it -> it.getProduct().getId().equals(dto.getProductId()) && !it.isPaid())
                .findFirst().orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + dto.getQuantity());
        } else {
            AccountItem item = new AccountItem();
            item.setAccount(account);
            item.setProduct(product);
            item.setQuantity(dto.getQuantity());
            item.setUnitPrice(product.getPrice());
            account.getItems().add(item);
        }

        Account saved = accountRepository.save(account);

        // 2) Si había un split activo, lo reiniciamos por completo:
        if (saved.getSplitTotal() != null) {
            // esto fuerza splitRemaining = splitTotal
            initOrUpdateSplit(accountId, saved.getSplitTotal());
            // recargo el estado
            saved = accountRepository.findById(accountId).get();
        }

        return saved;
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
     * Cierra la cuenta: genera una Sale a partir de todos los AccountItem, la
     * asocia a la caja abierta y al usuario autenticado (extraído del token), y
     * marca la entidad Account como closed=true.
     *
     * @param id ID de la cuenta a cerrar.
     * @param rawToken El header "Authorization" completo (p. ej. "Bearer
     * eyJ…").
     * @return La entidad Sale recién creada.
     */
    public SaleDTO closeAccount(Long id, String rawToken) {
        // 1) Buscamos la cuenta y validamos que no esté ya cerrada
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        if (account.isClosed()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La cuenta ya está cerrada"
            );
        }

        // 2) Obtenemos la caja abierta para la misma sucursal de la cuenta
        Long branchId = account.getBranch().getId();
        CashRegister cajaAbierta = cashRegisterService
                .getOpenCashRegisterForBranch(branchId);
        if (cajaAbierta == null) {
            throw new IllegalStateException(
                    "No hay ninguna caja abierta en la sucursal " + branchId
            );
        }

        // 3) Extraemos al usuario autenticado del token
        User usuarioActual = authService.getUserEntityFromToken(rawToken);
        if (usuarioActual == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Usuario no válido"
            );
        }

        // 4) Armar la nueva entidad Sale
        Sale sale = new Sale();
        sale.setBranch(account.getBranch());
        sale.setEstado(Sale.EstadoVenta.ACTIVA);
        sale.setDateTime(LocalDateTime.now());
        sale.setCashRegister(cajaAbierta);
        sale.setUser(usuarioActual);

        // 5) Recorrer cada AccountItem y convertirlo en SaleItem
        sale.setItems(new ArrayList<>());
        for (AccountItem item : account.getItems()) {
            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(item.getProduct());
            saleItem.setQuantity(item.getQuantity());
            saleItem.setUnitPrice(item.getUnitPrice());
            sale.getItems().add(saleItem);
        }

        // 6) Calcular totalAmount sumando cada (unitPrice × quantity)
        BigDecimal totalAmount = sale.getItems().stream()
                .map(i -> BigDecimal.valueOf(i.getUnitPrice())
                .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // —> Aquí establecemos el campo en la entidad antes de guardar:
        sale.setTotalAmount(totalAmount.doubleValue());

        // 7) Persistir la Sale completa (incluyendo su lista de SaleItem)
        saleRepository.save(sale);

        // 8) Marcar la cuenta como cerrada y persistir
        account.setClosed(true);
        accountRepository.save(account);

        // 9) Mapear la entidad Sale recién guardada a SaleDTO y devolverlo
        SaleDTO dto = new SaleDTO();
        dto.setId(sale.getId());
        dto.setItems(mapSaleItemsToDTOs(sale.getItems()));
        dto.setTotalAmount(totalAmount);
        dto.setPaymentMethod("CUENTA");                    // o lo que uses como método de pago
        dto.setDateTime(sale.getDateTime());                // asumiendo que en tu DTO dateTime es LocalDateTime

        dto.setKitchenSentAt(sale.getKitchenSentAt());      // si lo manejas así
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
        account.getItems().removeIf(item -> item.getId().equals(itemId));
        accountRepository.save(account);

        if (account.getSplitTotal() != null) {
            initOrUpdateSplit(accountId, account.getSplitTotal());
        }
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
        // 1) Buscamos la cuenta completa
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cuenta no encontrada: " + accountId
        ));

        // 2) Calculamos cuántas veces aparece cada itemId en la lista
        //    (puede venir repetido si el usuario marcó la misma “unidad” varias veces)
        Map<Long, Long> ocurrencias = itemIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        BigDecimal totalAmountToPay = BigDecimal.ZERO;

        // 3) Recorremos cada itemId y la cantidad de unidades que el usuario quiere pagar
        for (Map.Entry<Long, Long> entry : ocurrencias.entrySet()) {
            Long itemId = entry.getKey();
            Long unidadesAPagar = entry.getValue();

            // 3.a) Buscamos el AccountItem dentro de la lista de la cuenta
            AccountItem originalItem = account.getItems().stream()
                    .filter(ai -> ai.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El ítem " + itemId + " no existe en la cuenta " + accountId
            ));

            int cantidadOriginal = originalItem.getQuantity();          // ej: 2
            BigDecimal precioUnitario = BigDecimal.valueOf(originalItem.getUnitPrice()); // ej: 200

            if (unidadesAPagar < cantidadOriginal) {
                // ─────────────────────────────────────────────────────────────
                // Caso A: El usuario paga SOLO parte de las unidades de esta línea.
                //   Ejemplo: quantity=2, pero unidadesAPagar=1
                //
                // 1) Reducimos la línea original de cantidad:
                originalItem.setQuantity(cantidadOriginal - unidadesAPagar.intValue());
                //    Ahora quedará quantity = 2 - 1 = 1
                originalItem.setPaid(false);
                //    La línea original sigue sin estar “pagada en su totalidad”.
                //    (Hibernate detectará este cambio al guardar la cuenta más abajo.)

                // 2) Creamos una sub-línea nueva para las unidades que sí se pagaron:
                AccountItem lineaNuevaPagada = AccountItem.builder()
                        .account(account)
                        .product(originalItem.getProduct())
                        .quantity(unidadesAPagar.intValue()) // ej: 1 unidad pagada
                        .unitPrice(originalItem.getUnitPrice())
                        .paid(true)
                        .build();

                //    Si tu entidad AccountItem tiene otros campos (por ejemplo
                //    ingredientes), puedes copiarlos también:
                //    lineaNuevaPagada.setIngredientIds(originalItem.getIngredientIds());
                //    // …lo que corresponda según tu modelo.
                //
                account.getItems().add(lineaNuevaPagada);

                // 3) Calculamos el monto que corresponde a esas unidades pagadas:
                BigDecimal subtotal = precioUnitario.multiply(new BigDecimal(unidadesAPagar));
                totalAmountToPay = totalAmountToPay.add(subtotal);
            } else {
                // ─────────────────────────────────────────────────────────────
                // Caso B: El usuario paga EXACTAMENTE toda la línea (unidadesAPagar == cantidadOriginal).
                //
                // 1) Marcamos la línea original como pagada al 100%:
                originalItem.setPaid(true);

                // 2) Calculamos el monto: quantity × unitPrice
                BigDecimal subtotal = precioUnitario.multiply(new BigDecimal(cantidadOriginal));
                totalAmountToPay = totalAmountToPay.add(subtotal);
            }
        }

        // 4) Grabamos un registro en PaymentAccount con la suma total de todo lo pagado:
        PaymentAccount pago = new PaymentAccount();
        pago.setAccount(account);
        pago.setAmount(totalAmountToPay);
        pago.setPayerName((payerName == null || payerName.isBlank()) ? "–" : payerName);
        pago.setPaidAt(LocalDateTime.now());
        // Opcional: status PARTIALLY_PAID o PAID_IN_FULL según tu lógica.
        pago.setStatus(Status.PARTIALLY_PAID);

        paymentAccountRepository.save(pago);

        // 5) Finalmente salvamos la cuenta para que Hibernate persista:
        //    • los cambios en originalItem.quantity (si hubo split parcial)
        //    • o el cambio en originalItem.paid = true (si se pagó toda la línea)
        //    • y que se inserte la nueva línea “lineaNuevaPagada” en la colección.
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
    public OrderDTO closeAccountAndBuildReceipt(Long accountId, String rawToken) {
        log.info("▶ closeAccountAndBuildReceipt START: accountId={}", accountId);

        // 1) Invoco la lógica de cierre normal que devuelve un SaleDTO
        SaleDTO sale = closeAccount(accountId, rawToken);
        log.debug("   → closeAccount returned SaleDTO id={}, total={}", sale.getItems(), sale.getTotalAmount());

        // 2) Recupero la cuenta cerrada para extraer sucursal y compañía
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        Branch branch = account.getBranch();
        log.debug("   → Loaded branch for full closure: {}", branch.getName());

        // 3) Convierto directamente los SaleItemDTO que trae el SaleDTO
        List<SaleItemDTO> items = sale.getItems();
        log.debug("   → items for full closure receipt: {}", items);

        OrderDTO result = OrderDTO.builder()
                .id(sale.getId())
                .totalAmount(sale.getTotalAmount().doubleValue())
                .dateTime(sale.getDateTime().toString())
                .paymentMethod(sale.getPaymentMethod())
                .items(items)
                .branchName(branch.getName())
                .branchRut(branch.getRut())
                .branchAddress(branch.getLocation())
                .companyName(branch.getCompany().getName())
                .build();

        log.info("◀ closeAccountAndBuildReceipt END: generated OrderDTO={}", result);
        return result;
    }
}
