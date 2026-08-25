package com.superd.trigger.job;

import com.superd.domain.infoflow.service.IInfoFlowDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时刷新 InfoFlow；任务层仅调用领域服务。 */
@Slf4j
@Component
public class InfoFlowCollectJob {
    private final IInfoFlowDomainService infoFlowDomainService;

    public InfoFlowCollectJob(IInfoFlowDomainService infoFlowDomainService) {
        this.infoFlowDomainService = infoFlowDomainService;
    }

    @Scheduled(cron = "${infoflow.schedule.github-cron}")
    public void collectGitHubTrending() {
        try {
            log.info("InfoFlow GitHub 定时采集开始");
            log.info("InfoFlow GitHub 定时采集结果: {}", infoFlowDomainService.collectGitHubTrending());
        } catch (Exception e) {
            log.error("InfoFlow GitHub 定时采集失败；下一轮调度仍会继续", e);
        }
    }

    @Scheduled(cron = "${infoflow.schedule.news-cron}")
    public void collectIndustryNews() {
        try {
            log.info("InfoFlow Hacker News 定时采集开始");
            log.info("InfoFlow Hacker News 定时采集结果: {}", infoFlowDomainService.collectIndustryNews());
        } catch (Exception e) {
            log.error("InfoFlow Hacker News 定时采集失败；下一轮调度仍会继续", e);
        }
    }
}
