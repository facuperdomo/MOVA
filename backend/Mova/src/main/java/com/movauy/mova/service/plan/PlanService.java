package com.movauy.mova.service.plan;

import com.movauy.mova.model.plan.Plan;
import com.movauy.mova.repository.plan.PlanRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanService {
  private final PlanRepository repo;

  public List<Plan> listAll() {
    return repo.findAll();
  }

  public Plan getById(Long id) {
    return repo.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado: "+id));
  }

  public Plan create(Plan plan) {
    return repo.save(plan);
  }

  public Plan update(Long id, Plan updated) {
    Plan p = getById(id);
    BeanUtils.copyProperties(updated, p, "id", "createdAt");
    p.setUpdatedAt(LocalDateTime.now());
    return repo.save(p);
  }

  public void delete(Long id) {
    repo.deleteById(id);
  }
}
