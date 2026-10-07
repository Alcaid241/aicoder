package com.ai.coder.system.repository;

import com.ai.coder.system.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByStatusOrderBySortAsc(Integer status);

    List<Menu> findByIdInOrderBySortAsc(List<Long> ids);
}
