package com.softjourn.coin.server.entity;


import com.fasterxml.jackson.annotation.JsonView;
import com.softjourn.coin.server.util.JsonViews;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@NoArgsConstructor
@Data
public class Account {

    @Id
    @Column(name = "ldap_id")
    private String ldapId;

    @Column
    @JsonView(JsonViews.REGULAR.class)
    private BigDecimal amount;

    @Column
    private String fullName;

    @OneToOne(mappedBy = "account")
    private ErisAccount erisAccount;

    @Column
    @JsonView(JsonViews.REGULAR.class)
    private String image;

    public Account(String ldapId, BigDecimal amount) {
        this.amount = amount;
        this.ldapId = ldapId;
    }

    @JsonView(JsonViews.REGULAR.class)
    public String getName() {
        if (fullName == null) return "";
        String[] splitted = fullName.split("\\s");
        return splitted.length > 0 ? splitted[0] : "";
    }

    @JsonView(JsonViews.REGULAR.class)
    public String getSurname() {
        if (fullName == null) return "";
        String[] splitted = fullName.split("\\s");
        return splitted.length > 1 ? splitted[1] : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Account account = (Account) o;

        return ldapId.equals(account.ldapId);

    }

    @Override
    public int hashCode() {
        return ldapId.hashCode();
    }
}
