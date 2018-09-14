package com.opsian.performance_problems_example.core;

import com.opsian.performance_problems_example.db.PersonDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Bank
{
    private static final Object TRANSFER_LOCK = new Object();

    private static final Map<Long, Object> personIdToLock = new ConcurrentHashMap<>();

    private final PersonDAO personDAO;
    private final SessionFactory sessionFactory;

    public Bank(final PersonDAO personDAO, final SessionFactory sessionFactory)
    {
        this.personDAO = personDAO;
        this.sessionFactory = sessionFactory;
    }

    public boolean contendedTransferMoney(
        final long fromPersonId, final long toPersonId, final long amount)
    {
        synchronized (TRANSFER_LOCK)
        {
            final Person fromPerson = personDAO.findSafelyById(fromPersonId);
            final Person toPerson = personDAO.findSafelyById(toPersonId);

            if (fromPerson.getBankBalance() < amount)
            {
                return false;
            }

            fromPerson.withdraw(amount);
            toPerson.deposit(amount);

            return true;
        }
    }

    public boolean userLockingTransferMoney(
        final long fromPersonId, final long toPersonId, final long amount)
    {
        final Person fromPerson = personDAO.findSafelyById(fromPersonId);
        final Person toPerson = personDAO.findSafelyById(toPersonId);

        final Object fromLock = getLock(fromPersonId);
        final Object toLock = getLock(toPersonId);

        synchronized (fromLock)
        {
            if (fromPerson.getBankBalance() < amount)
            {
                return false;
            }

            fromPerson.withdraw(amount);
        }

        synchronized (toLock)
        {
            toPerson.deposit(amount);
        }

        return true;
    }

    private Object getLock(final long personId)
    {
        return personIdToLock.computeIfAbsent(personId, id -> new Object());
    }

    public boolean fastTransferMoney(
        final long fromPersonId, final long toPersonId, final long amount)
    {
        final Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery(
            "update Person f set f.bankBalance = f.bankBalance - :amount " +
                "where f.id = :fromId and f.bankBalance >= :amount")
            .setParameter("amount", amount)
            .setParameter("fromId", fromPersonId);

        if (query.executeUpdate() != 1)
        {
            System.out.println("failed to remove");
            return false;
        }

        query = session.createQuery(
            "update Person t set t.bankBalance = t.bankBalance + :amount where t.id = :toId")
            .setParameter("amount", amount)
            .setParameter("toId", toPersonId);

        return query.executeUpdate() == 1;
    }
}