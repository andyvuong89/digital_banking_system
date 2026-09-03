package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for: {}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())) {
            log.error("Account with email {} already exists", request.getEmail());
            throw new RuntimeException("Account already exists for email: " + request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS
                ? new BigDecimal(100000)
                : new BigDecimal(500000)
        );

        Account savedAccount = accountRepository.save(account);
        log.info("Account created with account number: {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    /**
     * Get account by account number
     * @param accountNumber
     * @return AccountResponse
     * */
    public AccountResponse getAccount(String accountNumber) {
        log.info("Fetching account details for account number: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));
        return mapToResponse(account);
    }

    /**
     * Get account balance by account number
     * @param accountNumber
     * @return BigDecimal
     * */
    public BigDecimal getBalance(String accountNumber) {
        log.info("Fetching balance for account number: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));
        return account.getBalance();
    }

    /**
     * Block account - called by Fraud detection Service via Kafka
     * @param accountNumber
     * */
    public void blockAccount(String accountNumber) {
        log.info("Blocking account with account number: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked successfully for account number: {}", accountNumber);
    }

    /**
     * Deduct balance from sender account
     * Called by Transaction Service
     * @param accountNumber
     * @param amount
     * */
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting {} from account number: {}", amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));

        if(account.getStatus() != AccountStatus.ACTIVE) {
            log.error("Account number: {} is not active. Current status: {}", accountNumber, account.getStatus());
            throw new RuntimeException("Account number: " + accountNumber + " is not active. Current status: " + account.getStatus());
        }

        if (account.getBalance().compareTo(amount) < 0) {
            log.error("Insufficient balance in account number: {}", accountNumber);
            throw new RuntimeException("Insufficient balance in account number: " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance deducted successfully for account number: {}", accountNumber);
    }

    /**
     * Credit balance to receiver account
     * Called by Transaction Service via Kafka
     * @param accountNumber
     * @param amount
     * */
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account number: {}", amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance credited successfully for account number: {}. New balance: {}", accountNumber, account.getBalance());
    }

    //    Generate a unique 12-digit account number
    private String generateAccountNumber() {
        String accountNumber;

        do {
            long number = secureRandom.nextLong(1_000_000_000L);

            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }
}
