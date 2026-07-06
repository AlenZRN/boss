package com.aimira.monitor.service;

import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.repository.ResourceInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** 保存或更新资源信息（按 resourceId + cloudProvider 去重） */
    public ResourceInfo saveOrUpdate(ResourceInfo resource) {
        try {
            Optional<ResourceInfo> existing = resourceInfoRepository
                    .findByResourceIdAndCloudProvider(resource.getResourceId(), resource.getCloudProvider());
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
        } catch (DataIntegrityViolationException e) {
            // 并发 INSERT 冲突（唯一约束），降级为 UPDATE
            log.debug("并发冲突，降级为 UPDATE: resourceId={}, cloudProvider={}",
                    resource.getResourceId(), resource.getCloudProvider());
            ResourceInfo existing = resourceInfoRepository
                    .findByResourceIdAndCloudProvider(resource.getResourceId(), resource.getCloudProvider())
                    .orElseThrow(() -> new IllegalStateException(
                            "唯一约束冲突但查询不到记录: resourceId=" + resource.getResourceId()
                            + ", cloudProvider=" + resource.getCloudProvider(), e));
            existing.setResourceName(resource.getResourceName());
            existing.setResourceType(resource.getResourceType());
            existing.setRegion(resource.getRegion());
            existing.setStatus(resource.getStatus());
            existing.setExpireTime(resource.getExpireTime());
            existing.setSyncTime(resource.getSyncTime());
            return resourceInfoRepository.save(existing);
        }
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

    /** 获取即将到期的资源（分页，支持搜索和云厂商筛选） */
    public Page<ResourceInfo> getExpiringResources(String keyword, String cloudProvider, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return resourceInfoRepository.searchExpiringResources(keyword, cloudProvider, pageable);
        }
        // 默认查询30天内到期的
        LocalDateTime threshold = LocalDateTime.now().plusDays(30);
        return resourceInfoRepository.findExpiringResources(threshold, cloudProvider, pageable);
    }

    /** 统计即将到期的资源数量（默认30天内） */
    public long countExpiringResources(int days, String cloudProvider) {
        LocalDateTime threshold = LocalDateTime.now().plusDays(days);
        return resourceInfoRepository.countExpiringResources(threshold, cloudProvider);
    }

    /** 多条件分页查询资源 */
    public Page<ResourceInfo> searchResources(String keyword, String resourceType,
                                               String region, String status, String cloudProvider,
                                               Pageable pageable) {
        return resourceInfoRepository.searchResources(keyword, resourceType, region, status, cloudProvider, pageable);
    }

    /** 按资源类型统计数量 */
    public List<Object[]> countByResourceType(String cloudProvider) {
        return resourceInfoRepository.countByResourceType(cloudProvider);
    }

    /** 按地域统计数量 */
    public List<Object[]> countByRegion(String cloudProvider) {
        return resourceInfoRepository.countByRegion(cloudProvider);
    }
}
