package com.movauy.mova.controller.plan;

import com.movauy.mova.model.plan.Plan;
import com.movauy.mova.service.plan.PlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {
  private final PlanService service;

  @GetMapping
  public List<Plan> all() {
    return service.listAll();
  }

  @GetMapping("/{id}")
  public Plan one(@PathVariable Long id) {
    return service.getById(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Plan create(@RequestBody Plan plan) {
    return service.create(plan);
  }

  @PutMapping("/{id}")
  public Plan update(@PathVariable Long id, @RequestBody Plan plan) {
    return service.update(id, plan);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
  
}
