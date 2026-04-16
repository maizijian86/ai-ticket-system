package com.aiticket.user.repository;

import com.aiticket.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    Optional<UserSkill> findByUserId(Long userId);

    List<UserSkill> findByUserIdIn(List<Long> userIds);

    @Query("SELECT us FROM UserSkill us WHERE us.currentLoad < us.maxLoad AND us.user.status = 'active'")
    List<UserSkill> findAvailableHandlers();

    @Modifying
    @Query("UPDATE UserSkill us SET us.currentLoad = us.currentLoad + 1 WHERE us.userId = :userId AND us.currentLoad < us.maxLoad")
    int incrementLoad(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserSkill us SET us.currentLoad = us.currentLoad - 1 WHERE us.userId = :userId AND us.currentLoad > 0")
    int decrementLoad(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserSkill us SET us.totalResolved = us.totalResolved + 1, " +
           "us.avgResolutionHours = (us.avgResolutionHours * us.totalResolved + :hours) / (us.totalResolved + 1) " +
           "WHERE us.userId = :userId")
    void updateResolvedMetrics(@Param("userId") Long userId, @Param("hours") BigDecimal hours);
}
