package com.softjourn.coin.server.aop.aspects;


import com.softjourn.coin.server.aop.annotations.SaveTransaction;
import com.softjourn.coin.server.entity.Account;
import com.softjourn.coin.server.entity.Transaction;
import com.softjourn.coin.server.entity.TransactionStatus;
import com.softjourn.coin.server.repository.AccountRepository;
import com.softjourn.coin.server.repository.TransactionRepository;
import com.softjourn.coin.server.service.CoinService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Aspect
@Order(value = 100)
@Service
public class TransactionSavingAspect {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CoinService coinService;

    @Around("@annotation(com.softjourn.coin.server.aop.annotations.SaveTransaction)")
    public Object saveTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        Transaction transaction = new Transaction();
        try {
            Object callingResult = joinPoint.proceed();
            if (callingResult instanceof Transaction) {
                transaction = (Transaction) callingResult;
            }
            prepareTransaction(transaction, joinPoint);
            transaction.setStatus(TransactionStatus.SUCCESS);
            setRemainAmount((MethodSignature) joinPoint.getSignature(), joinPoint.getArgs(), transaction);
            return callingResult instanceof Transaction ? transactionRepository.save(transaction) : callingResult;
        } catch (Throwable e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setError(e.getLocalizedMessage());
            prepareTransaction(transaction, joinPoint);
            throw e;
        } finally {
            transactionRepository.save(transaction);
        }
    }

    private Transaction prepareTransaction(Transaction transaction, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return fillTransaction(transaction, signature, joinPoint.getArgs());
    }

    private Transaction fillTransaction(Transaction transaction, MethodSignature signature, Object[] arguments) {
        replaceIfNull(transaction::getAccount, transaction::setAccount, getAccount(signature, arguments, "accountName"));
        replaceIfNull(transaction::getDestination, transaction::setDestination, getDestination(signature, arguments, "destinationName"));
        replaceIfNull(transaction::getAmount, transaction::setAmount, getArg(signature, arguments, "amount", BigDecimal.class));
        replaceIfNull(transaction::getComment, transaction::setComment, getArg(signature, arguments, "comment", String.class));
        transaction.setCreated(Instant.now());
        return transaction;
    }

    private <T> void replaceIfNull(Supplier<T> getter, Consumer<T> setter, T value) {
        if (getter.get() == null) {
            setter.accept(value);
        }
    }

    private void setRemainAmount(MethodSignature signature, Object[] arguments, Transaction transaction) {
        BigDecimal remain;
        String accName = Optional.ofNullable(transaction.getAccount())
                .map(Account::getLdapId)
                .orElseGet(() -> getArg(signature, arguments, "accountName", String.class));
        if (accName != null) {
            remain = coinService.getAmount(accName);
            transaction.setRemain(remain);
        }
    }

    private Account getAccount(MethodSignature signature, Object[] arguments, String argName) {
        SaveTransaction annotation = signature.getMethod().getAnnotation(SaveTransaction.class);
        String accountName = annotation.accountName().isEmpty()
                ? getArg(signature, arguments, argName, String.class)
                : annotation.accountName();

        return accountName == null ? null : accountRepository.findOne(accountName);
    }

    private Account getDestination(MethodSignature signature, Object[] arguments, String argName) {
        SaveTransaction annotation = signature.getMethod().getAnnotation(SaveTransaction.class);
        String accountName = annotation.destinationName().isEmpty()
                ? getArg(signature, arguments, argName, String.class)
                : annotation.destinationName();

        return accountName == null ? null : accountRepository.findOne(accountName);
    }


    @SuppressWarnings("unchecked")
    private <T> T getArg(MethodSignature signature, Object[] arguments, String name, Class<? extends T> clazz) {
        String[] names = signature.getParameterNames();
        for (int i = 0; i < arguments.length; i++) {
            if (names[i].equalsIgnoreCase(name)) {
                Object res = arguments[i];
                if (clazz.isInstance(res)) {
                    return (T) res;
                } else {
                    return null;
                }
            }
        }
        return null;
    }
}
