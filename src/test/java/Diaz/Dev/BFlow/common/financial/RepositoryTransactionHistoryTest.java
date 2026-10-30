package Diaz.Dev.BFlow.common.financial;

import bflow.common.financial.RepositoryTransactionHistory;
import bflow.common.financial.TransactionResponse;
import bflow.common.financial.TransactionSearchCriteria;
import bflow.common.financial.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryTransactionHistoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private RepositoryTransactionHistory repository;
    private String rowWalletId;
    private String rowCategoryId;
    private String rowContributorId;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = new RepositoryTransactionHistory(jdbc);
    }

    @Test
    void emptyWalletListReturnsAnEmptyPageWithoutQueryingDatabase() {
        PageRequest pageable = PageRequest.of(0, 10);

        Page<TransactionResponse> result = repository.search(
                List.of(),
                TransactionSearchCriteria.global(null, null, null),
                pageable);

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getNumberOfElements());
        verify(jdbc, org.mockito.Mockito.never())
                .query(anyString(), any(MapSqlParameterSource.class),
                        any(RowMapper.class));
    }

    @Test
    void filtersOneTypeAndMapsCollaboratorAndTransactionFields() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID otherWalletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        rowWalletId = otherWalletId.toString();
        rowCategoryId = categoryId.toString();
        rowContributorId = contributorId.toString();
        stubQuery(TransactionType.EXPENSE, 2, Instant.parse(
                "2026-03-04T05:06:07Z"));
        when(jdbc.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(6L);
        PageRequest pageable = PageRequest.of(
                1, 5, Sort.by(Sort.Direction.ASC, "amount"));

        Page<TransactionResponse> result = repository.search(
                List.of(walletId),
                TransactionSearchCriteria.forWallet(
                        " cafe ", walletId, TransactionType.EXPENSE,
                        List.of(contributorId)),
                pageable);

        assertEquals(6, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(1, result.getNumberOfElements());
        TransactionResponse response = result.getContent().get(0);
        assertEquals("transaction-1", response.getId());
        assertEquals(TransactionType.EXPENSE, response.getType());
        assertEquals("Lunch", response.getTitle());
        assertEquals(new BigDecimal("-12.50"), response.getAmount());
        assertEquals(Instant.parse("2026-03-04T05:06:07Z"),
                response.getDate());
        assertEquals(otherWalletId.toString(), response.getWalletId());
        assertEquals("Shared wallet", response.getWalletName());
        assertEquals(categoryId.toString(), response.getCategoryId());
        assertEquals(contributorId.toString(), response.getContributorId());
        assertEquals("A collaborator", response.getContributorName());
        assertEquals("member@example.com", response.getContributorEmail());
        assertEquals("https://example.com/avatar.png",
                response.getContributorPictureUrl());
        assertEquals("manual", response.getSource());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(),
                any(RowMapper.class));
        assertTrue(sql.getValue().contains("'EXPENSE'"));
        assertTrue(sql.getValue().contains("ORDER BY amount ASC"));
        assertEquals("%CAFE%",
                parameters.getValue().getValue("queryPattern"));
        assertEquals(List.of(contributorId),
                parameters.getValue().getValue("contributorIds"));
        assertEquals(5, parameters.getValue().getValue("limit"));
        assertEquals(5L, parameters.getValue().getValue("offset"));
    }

    @Test
    void globalSearchUsesUnionDefaultSortAndHidesSoloContributorDetails()
            throws Exception {
        rowWalletId = UUID.randomUUID().toString();
        rowCategoryId = UUID.randomUUID().toString();
        rowContributorId = UUID.randomUUID().toString();
        stubQuery(TransactionType.TRANSFER, 1, null);
        when(jdbc.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);

        Page<TransactionResponse> result = repository.search(
                List.of(UUID.randomUUID()),
                TransactionSearchCriteria.global("  ", null, List.of()),
                PageRequest.of(0, 10, Sort.by("not-whitelisted")));

        assertEquals(1, result.getTotalElements());
        TransactionResponse response = result.getContent().get(0);
        assertNull(response.getDate());
        assertNull(response.getContributorName());
        assertNull(response.getContributorEmail());
        assertNull(response.getContributorPictureUrl());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(),
                any(RowMapper.class));
        assertEquals(2, sql.getValue().split("UNION ALL", -1).length - 1);
        assertTrue(sql.getValue().contains("ORDER BY txn_date DESC"));
        assertFalse((Boolean) parameters.getValue().getValue("hasQuery"));
        assertEquals(List.of(new UUID(0L, 0L)),
                parameters.getValue().getValue("contributorIds"));
    }

    private void stubQuery(
            final TransactionType type,
            final int memberCount,
            final Instant date
    ) throws Exception {
        when(jdbc.query(
                anyString(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<TransactionResponse> mapper = invocation.getArgument(2);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("id")).thenReturn("transaction-1");
            when(rs.getString("type")).thenReturn(type.name());
            when(rs.getString("title")).thenReturn("Lunch");
            when(rs.getString("description")).thenReturn("Cafe");
            when(rs.getBigDecimal("amount"))
                    .thenReturn(new BigDecimal("-12.50"));
            when(rs.getTimestamp("txn_date")).thenReturn(
                    date == null ? null : Timestamp.from(date));
            when(rs.getString("wallet_id")).thenReturn(rowWalletId);
            when(rs.getString("wallet_name")).thenReturn("Shared wallet");
            when(rs.getString("counterpart_wallet_id")).thenReturn(null);
            when(rs.getString("counterpart_wallet_name")).thenReturn(null);
            when(rs.getString("category_id")).thenReturn(rowCategoryId);
            when(rs.getString("category_name")).thenReturn("Food");
            when(rs.getString("category_icon")).thenReturn("utensils");
            when(rs.getString("category_color")).thenReturn("#123456");
            when(rs.getString("contributor_id"))
                    .thenReturn(rowContributorId);
            when(rs.getString("contributor_name"))
                    .thenReturn("A collaborator");
            when(rs.getString("contributor_email"))
                    .thenReturn("member@example.com");
            when(rs.getString("contributor_picture_url"))
                    .thenReturn("https://example.com/avatar.png");
            when(rs.getInt("member_count")).thenReturn(memberCount);
            when(rs.getString("status")).thenReturn("COMPLETED");
            when(rs.getString("source")).thenReturn("manual");
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
