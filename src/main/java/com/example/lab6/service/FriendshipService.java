package com.example.lab6.service;

import com.example.lab6.model.*;
import com.example.lab6.model.validators.ValidationException;
import com.example.lab6.repository.UserRepository;
import com.example.lab6.repository.paging.PagingRepository;
import com.example.lab6.utils.events.UserChangeEvent;
import com.example.lab6.utils.observer.Observable;
import com.example.lab6.utils.observer.Observer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FriendshipService implements Observable<UserChangeEvent> {
    UserRepository<Long, User> repoUser;
    PagingRepository<Tuple<Long, Long>, Friendship> repoFriendship;

    /**
     * @param repoUser
     * @param repoFriendship
     */
    public FriendshipService(UserRepository repoUser, PagingRepository<Tuple<Long, Long>, Friendship> repoFriendship) {
        this.repoUser = repoUser;
        this.repoFriendship = repoFriendship;
    }

    /**
     * @param entity
     * @return entity if saved
     * throw Validate Exception if the ID already exists or it is invalid
     */
    public Friendship add(Friendship entity) {
        Long id1 = entity.getE1();
        Long id2 = entity.getE2();

        if (repoUser.findOne(id1) == null || repoUser.findOne(id2) == null)
            throw new ValidationException("Id invalid");
        if (repoFriendship.findOne(entity.getId()) != null)
            throw new ValidationException("Already exists");

        repoUser.findOne(id1).addFriend(repoUser.findOne(id2));
        repoUser.findOne(id2).addFriend(repoUser.findOne(id1));
        LocalDateTime dateTime = LocalDateTime.now();

        entity.setDate(dateTime);
        return repoFriendship.save(entity);
    }

    public Friendship exists(Long id1, Long id2) {

        Tuple<Long, Long> ship = new Tuple<>(id1, id2);
        if (repoFriendship.findOne(ship) != null)
            return repoFriendship.findOne(ship);

        return null;

    }

    /**
     * @param id1 must be not null
     * @param id2 must be not null
     */
    public void removeFriendship(Long id1, Long id2) {
        if (repoUser.findOne(id1) == null || repoUser.findOne(id2) == null)
            throw new ValidationException("Nu exista");
        Tuple<Long, Long> ship = new Tuple<>(id1, id2);
        repoFriendship.remove(repoFriendship.findOne(ship));
        //repoFriendship.setFriendships();
    }

    /**
     * return all the friendships of a user
     *
     * @param id
     * @return
     */
    public List<FriendshipDTO> getFriendships(Long id) {
        if (repoUser.findOne(id) == null)
            throw new ValidationException("Invalid id");
        User user = repoUser.findOne(id);
        List<FriendshipDTO> friendslist = new ArrayList<>();
        Iterable<Friendship> friendshipIterable = repoFriendship.findAll();

        Predicate<Friendship> firstfriend = x -> x.getE1().equals(id);
        Predicate<Friendship> secondfriend = x -> x.getE2().equals(id);
        Predicate<Friendship> friendshipPredicate = firstfriend.or(secondfriend);
        List<Friendship> list = new ArrayList<>();
        friendshipIterable.forEach(list::add);
        list.stream().filter(friendshipPredicate).map(x -> {
            if (x.getE1().equals(id))
                return new FriendshipDTO(repoUser.findOne(x.getE2()), x.getDate());
            else
                return new FriendshipDTO(repoUser.findOne(x.getE1()), x.getDate());
        }).forEach(friendslist::add);

        return friendslist;
    }

    private final List<Observer<UserChangeEvent>> observers = new ArrayList<>();

    @Override
    public void addObserver(Observer<UserChangeEvent> e) {
        observers.add(e);
    }

    @Override
    public void notifyObservers(UserChangeEvent t) {
        observers.forEach(x -> x.update(t));
    }

    @Override
    public void removeObserver(Observer<UserChangeEvent> e) {

    }

    public List<FriendshipDTO> getMyFriendsOnPage(int leftLimit, int rightLimit, Long id) {
        List<FriendshipDTO> friendslist = getFriendships(id);
        return friendslist.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
    }

}
