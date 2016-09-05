package com.softjourn.coin.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softjourn.coin.server.entity.Account;
import com.softjourn.coin.server.entity.ErisAccount;
import com.softjourn.coin.server.entity.ErisAccountType;
import com.softjourn.coin.server.repository.AccountRepository;
import com.softjourn.coin.server.repository.ErisAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@Service
public class ErisAccountsService {

    private AccountRepository accountRepository;

    private ErisAccountRepository repository;

    private RestTemplate restTemplate;

    private ResourceLoader resourceLoader;

    @Value("${eris.accounts.json.path}")
    private String accountsJsonPath;


    private static final String CHAIN_PARTICIPANT = ".*_participant_.*";
    private static final String CHAIN_ROOT = ".*_root_.*";


    @Autowired
    public ErisAccountsService(ErisAccountRepository repository,
                               RestTemplate restTemplate,
                               ResourceLoader resourceLoader,
                               AccountRepository accountRepository) throws IOException {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.restTemplate = restTemplate;
        this.resourceLoader = resourceLoader;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void init() throws IOException {
        File erisJsonFile = resourceLoader.getResource("classpath:" + accountsJsonPath).getFile();
        TreeMap<String, ErisAccount> erisAccountMap=erisAccountMapping(erisJsonFile);
        LinkedList<ErisAccount> newAssignedErisAccounts = shareAccounts(erisAccountMap);
        repository.save(newAssignedErisAccounts);
        repository.save(erisAccountMap.values());
    }

    public TreeMap<String, ErisAccount> erisAccountMapping(File erisJsonFile) throws IOException{
        TreeMap<String,ErisAccount> erisAccountMap = new TreeMap<>();
        ObjectMapper mapper = new ObjectMapper();

        Map<String, ErisAccount> accountMap;
        accountMap = mapper.readValue(erisJsonFile, new TypeReference<Map<String, ErisAccount>>() {
        });
        accountMap.forEach((k, v) -> {
            if (k.matches(CHAIN_ROOT)) {
                v.setType(ErisAccountType.ROOT);

            } else if (k.matches(CHAIN_PARTICIPANT)) {
                    v.setType(ErisAccountType.PARTICIPANT);
            }
            erisAccountMap.put(v.getAddress(), v);

        });

        return erisAccountMap;
    }

    private LinkedList<ErisAccount> shareAccounts(TreeMap<String, ErisAccount> accountCollection) {

        LinkedList<Account> linkedAccounts = new LinkedList<>(accountRepository.getAll());
        LinkedList<ErisAccount> newAssignedErisAccounts = new LinkedList<>();

        linkedAccounts.stream()
                .filter(account -> account.getErisAccount() != null)
                .forEach(account -> {
                            ErisAccount existingAccount = accountCollection.get(account.getErisAccount().getAddress());
                            if(existingAccount==null) {
                                repository.delete(account.getErisAccount());
                                account.setErisAccount(null);
                            }
                            else {
                                if (existingAccount.equals(account.getErisAccount()))
                                    accountCollection.remove(account.getErisAccount().getAddress());
                                else {
                                    ErisAccount newEris = accountCollection.remove(account.getErisAccount().getAddress());
                                    newEris.setAccount(account);
                                    newAssignedErisAccounts.add(newEris);
                                }
                            }
                        }
                );
        linkedAccounts.stream()
                .filter(account -> account.getErisAccount() == null)
                .forEach(account -> {
                    ErisAccount newEris = accountCollection.pollFirstEntry().getValue();
                    newEris.setAccount(account);
                    newAssignedErisAccounts.add(newEris);
                });

        return newAssignedErisAccounts;

    }

    public ErisAccount bindFreeAccount() {
        return repository
                .getFree()
                .findFirst()
                .orElse(null);
    }

    public List<ErisAccount> getAll() {
        return StreamSupport
                .stream(repository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

}
