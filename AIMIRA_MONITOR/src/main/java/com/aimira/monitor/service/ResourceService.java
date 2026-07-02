package com.aimira.monitor.service;

import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.repository.ResourceInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ResourceService {

    private final ResourceInfoRepository resourceInfoRepository;

    public ResourceService(ResourceInfoRepository resourceInfoRepository) {
        this.resourceInfoRepository = resourceInfoRepository;
    }

    /** 保存或更新资源信息 */
    public ResourceInfo saveOrUpdate(ResourceInfo resource) {
        Optional<ResourceInfo> existing = resourceInfoRepository.findByResourceId(resource.getResourceId());
        if (existing.isPresent()) {
            ResourceInfo updated = existing.get();
            updated.setResourceName(resource.getResourceName());
            updated.setResourceType(resource.getResourceType());
            updated.setRegion(resource.getRegion());
            updated.setStatus(resource.getStatus());
            updated.setExpireTime(resource.getExpireTime());
            updated.setSyncTime(resource.getSyncTime());
            return resourceInfoRepository.save(updated);
        }
        return resourceInfoRepository.save(resource);
    }

    /** 批量保存或更新资源 */
    public void saveOrUpdateBatch(List<ResourceInfo> resources) {
        for (ResourceInfo resource : resources) {
            saveOrUpdate(resource);
        }
    }

    /** 获取所有有到期时间的资源 */
    public List<ResourceInfo> getResourcesWithExpiry() {
        return resourceInfoRepository.findResourcesWithExpiry();
    }

    /** 获取即将到期的资源（分页，支持搜索） */
    public Page<ResourceInfo> getExpiringResources(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return resourceInfoRepository.searchExpiringResources(keyword, pageable);
        }
        // 默认查询30天内到期的
        LocalDateTime threshold = LocalDateTime.now().plusDays(30);
        return resourceInfoRepository.findExpiringResources(threshold, pageable);
    }

    /** 统计即将到期的资源数量（默认30天内） */
    public long countExpiringResources(int days) {
        LocalDateTime threshold = LocalDateTime.now().plusDays(days);
        return resourceInfoRepository.countExpiringResources(threshold);
    }

    /** 多条件分页查询资源 */
    public Page<ResourceInfo> searchResources(String keyword, String resourceType,
                                               String region, String status, Pageable pageable) {
        return resourceInfoRepository.searchResources(keyword, resourceType, region, status, pageable);
    }

    /** 按资源类型统计数量 */
    public List<Object[]> countByResourceType() {
        return resourceInfoRepository.countByResourceType();
    }

    /** 按地域统计数量 */
    public List<Object[]> countByRegion() {
        return resourceInfoRepository.countByRegion();
    }
}
