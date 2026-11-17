package com.liana.post.oms.service;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.mapper.MailMapper;
import com.liana.post.oms.model.entity.MailEntity;
import com.liana.post.oms.repository.MyBatisOmsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegacyPackingGuardTest {
    @Test
    void legacyAssignmentCannotMoveMailDirectlyBetweenUnopenedBags() {
        var mapper = mock(MailMapper.class); var jdbc = mock(JdbcTemplate.class);
        var repository = new MyBatisOmsRepository(null, null, null, mapper, null, null, null, jdbc);
        var mail = new MailEntity(); mail.setBagNo("P1"); mail.setPackageId("P1"); mail.setCurrentFacilityCode("LN-OR-01");
        when(mapper.selectOne(any())).thenReturn(mail);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("P2"))).thenReturn(0L);

        assertEquals(409, assertThrows(BusinessException.class,
                () -> repository.assignMailBag("CP000000001LN", "P2", "LN-OR-01")).getCode());
        verify(mapper, never()).updateById(any());
        assertEquals("P1", mail.getBagNo());
    }

    @Test
    void legacyAssignmentCannotAppendMailToConfirmedDispatchPacking() {
        var mapper = mock(MailMapper.class); var jdbc = mock(JdbcTemplate.class);
        var repository = new MyBatisOmsRepository(null, null, null, mapper, null, null, null, jdbc);
        var mail = new MailEntity(); mail.setCurrentFacilityCode("LN-OR-01");
        when(mapper.selectOne(any())).thenReturn(mail);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("P1"))).thenReturn(1L);

        assertEquals(409, assertThrows(BusinessException.class,
                () -> repository.assignMailBag("CP000000001LN", "P1", "LN-OR-01")).getCode());
        verify(mapper, never()).updateById(any());
        assertNull(mail.getBagNo());
    }
}
