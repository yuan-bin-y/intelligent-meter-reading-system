package com.byy.meterreading.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 根据角色或抄表任务关系查找通知接收人。 */
@Mapper
public interface NotificationRecipientMapper {

    List<Long> selectEnabledUserIdsByRoleCodes(
            @Param("roleCodes") List<String> roleCodes
    );

    List<Long> selectTaskParticipantUserIds(
            @Param("taskId") Long taskId
    );
}
