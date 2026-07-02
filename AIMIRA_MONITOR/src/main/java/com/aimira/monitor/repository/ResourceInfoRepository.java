package com.aimira.monitor.repository;

import com.aimira.monitor.entity.ResourceInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceInfoRepository extends JpaRepository<ResourceInfo, Long> {

    /** 根据阿里云 Resource ID 查找 */
    Optional<ResourceInfo> findByResourceId(String resourceId);

    /** 查找有到期时间的资源，按到期时间升序 */
    @Query("SELECT r FROM ResourceInfo r WHERE r.expireTime IS NOT NULL ORDER BY r.expireTime ASC")
    List<ResourceInfo> findResourcesWithExpiry();

    /** 查找即将到期的资源（分页） */
    @Query("SELECT r FROM ResourceInfo r WHERE r.expireTime IS NOT NULL AND r.expireTime <= :threshold ORDER BY r.expireTime ASC")
    Page<ResourceInfo> findExpiringResources(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    /** 搜索到期资源 */
    @Query("SELECT r FROM ResourceInfo r WHERE r.expireTime IS NOT NULL " +
           "AND (r.resourceName LIKE %:keyword% OR r.resourceId LIKE %:keyword%) " +
           "ORDER BY r.expireTime ASC")
    Page<ResourceInfo> searchExpiringResources(@Param("keyword") String keyword, Pageable pageable);

    /** 统计即将到期的资源数量 */
    @Query("SELECT COUNT(r) FROM ResourceInfo r WHERE r.expireTime IS NOT NULL AND r.expireTime <= :threshold")
    long countExpiringResources(@Param("threshold") LocalDateTime threshold);

    /** 多条件查询资源（分页）：关键字匹配名称或ID，可选类型/地域/状态过滤 */
    @Query("SELECT r FROM ResourceInfo r WHERE " +
           "(:keyword IS NULL OR r.resourceName LIKE %:keyword% OR r.resourceId LIKE %:keyword%) " +
           "AND (:resourceType IS NULL OR r.resourceType = :resourceType) " +
           "AND (:region IS NULL OR r.region = :region) " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<ResourceInfo> searchResources(@Param("keyword") String keyword,
                                       @Param("resourceType") String resourceType,
                                       @Param("region") String region,
                                       @Param("status") String status,
                                       Pageable pageable);

    /** 按资源类型统计数量 */
    @Query("SELECT r.resourceType, COUNT(r) FROM ResourceInfo r GROUP BY r.resourceType")
    List<Object[]> countByResourceType();

    /** 按地域统计数量 */
    @Query("SELECT r.region, COUNT(r) FROM ResourceInfo r GROUP BY r.region")
    List<Object[]> countByRegion();
}
