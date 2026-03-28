package com.metrology.repository;

import com.metrology.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("SELECT d FROM Device d WHERE " +
           "(:search IS NULL OR d.name LIKE %:search% OR d.metricNo LIKE %:search% OR d.responsiblePerson LIKE %:search%) AND " +
           "(:assetNo IS NULL OR d.assetNo LIKE %:assetNo%) AND " +
           "(:serialNo IS NULL OR d.serialNo LIKE %:serialNo%) AND " +
           "(:dept IS NULL OR d.dept = :dept) AND " +
           "(:validity IS NULL OR d.validity = :validity) AND " +
           "(:useStatus IS NULL OR d.useStatus = :useStatus)")
    List<Device> findWithFilters(@Param("search") String search,
                                  @Param("assetNo") String assetNo,
                                  @Param("serialNo") String serialNo,
                                  @Param("dept") String dept,
                                  @Param("validity") String validity,
                                  @Param("useStatus") String useStatus);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.nextDate >= :startDate AND d.nextDate <= :endDate")
    long countByNextDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT YEAR(d.calDate) as yr, MONTH(d.calDate) as mo, COUNT(d) as cnt " +
           "FROM Device d WHERE d.calDate >= :since GROUP BY YEAR(d.calDate), MONTH(d.calDate) ORDER BY yr, mo")
    List<Object[]> countByCalDateMonth(@Param("since") LocalDate since);

    @Query("SELECT YEAR(d.calDate) as yr, MONTH(d.calDate) as mo, COUNT(d) as cnt " +
           "FROM Device d WHERE d.calDate >= :since AND d.dept = :dept " +
           "GROUP BY YEAR(d.calDate), MONTH(d.calDate) ORDER BY yr, mo")
    List<Object[]> countByCalDateMonthAndDept(@Param("since") LocalDate since, @Param("dept") String dept);
}
