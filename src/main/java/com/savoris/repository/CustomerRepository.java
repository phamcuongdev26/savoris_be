package com.savoris.repository;

import com.savoris.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Integer> {
    Optional<CustomerEntity> findByPhone(String phone);
    Optional<CustomerEntity> findByEmail(String email);
    List<CustomerEntity> findByFullNameContainingIgnoreCase(String fullName);

    // Spends points only if the customer still has enough, so two orders cannot spend the same points twice
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerEntity c set c.points = c.points - :points where c.id = :id and c.points >= :points")
    int spendPoints(@Param("id") Integer id, @Param("points") int points);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerEntity c set c.points = c.points + :points, c.totalSpent = c.totalSpent + :spent where c.id = :id")
    int addPoints(@Param("id") Integer id, @Param("points") int points, @Param("spent") BigDecimal spent);
}
