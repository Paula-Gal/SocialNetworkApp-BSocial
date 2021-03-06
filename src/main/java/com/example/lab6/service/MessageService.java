package com.example.lab6.service;

import com.example.lab6.model.*;
import com.example.lab6.model.validators.ValidationException;
import com.example.lab6.repository.Repository;
import com.example.lab6.repository.UserRepository;
import com.example.lab6.repository.paging.Pageable;
import com.example.lab6.repository.paging.PagingRepository;
import com.example.lab6.utils.events.ChangeEventType;
import com.example.lab6.utils.events.MessageChangeEvent;
import com.example.lab6.utils.observer.Observable;
import com.example.lab6.utils.observer.Observer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MessageService implements Observable<MessageChangeEvent> {
    PagingRepository<Long, MessageDTO> repoMessage;
    UserRepository<Long, User> repoUser;
    Repository<Tuple<Long, Long>, Friendship> repoFriendship;
    Repository<Long, Group> repoGroup;

    /**
     * Constructor
     *
     * @param repoMessage
     * @param repoUser
     */
    public MessageService(PagingRepository<Long, MessageDTO> repoMessage, UserRepository<Long, User> repoUser, Repository<Tuple<Long, Long>, Friendship> repoFriendship, Repository<Long, Group> repoGroup) {
        this.repoMessage = repoMessage;
        this.repoUser = repoUser;
        this.repoFriendship = repoFriendship;
        this.repoGroup = repoGroup;
    }

    /**
     * send a message to a user
     *
     * @param from
     * @param to
     * @param message
     */
    public void sendMessage(Long from, List<Long> to, String message) {
        if (repoUser.findOne(from) == null)
            throw new ValidationException("The user doesn't exist!");

        List<Long> existentFriendship = new ArrayList<>();
        for (Long user : to) {

            if ((repoFriendship.findOne(new Tuple<>(from, user)) != null)) {
                existentFriendship.add(user);
            }
        }

        if (existentFriendship.size() > 0) {
            List<Long> distinctTos = existentFriendship.stream().distinct().collect(Collectors.toList());
            distinctTos.forEach(
                    x -> {
                        if (repoUser.findOne(x) == null)
                            throw new ValidationException("The user doesn't exist!");
                    }
            );

            if (message.length() == 0)
                throw new ValidationException("The message is empty!");

            MessageDTO messageDTO = new MessageDTO(from, to, message, LocalDateTime.now(), null);
            repoMessage.save(messageDTO);
            notifyObservers(new MessageChangeEvent(ChangeEventType.ADD, messageDTO));
        } else
            throw new ValidationException("The user has no friends!");

        if (existentFriendship.size() != to.size())
            throw new ValidationException("The message was sent only to friends!");
    }


    public void sendMessageGroup(Group group, MessageDTO messageDTO) {
        group.addMessage(messageDTO);
        repoGroup.update(group);
        notifyObservers(new MessageChangeEvent(ChangeEventType.ADD, messageDTO));
    }

    /**
     * reply to a message
     *
     * @param toMessage
     * @param fromUser
     * @param message
     */
    public void replyToOne(Long toMessage, Long fromUser, String message) {
        if (repoMessage.findOne(toMessage) == null)
            throw new ValidationException("The message do not exist!");
        if (repoUser.findOne(fromUser) == null)
            throw new ValidationException("The user do not exist!");
        if (message.length() == 0)
            throw new ValidationException("The message is empty!");

        List<Long> toUsersList = new ArrayList<>();
        List<Long> recipients = new ArrayList<>();
        //toUsers.add(fromUser);
        repoMessage.findOne(toMessage).getTo().forEach(x -> {
            User user = new User(repoUser.findOne(x).getFirstName(), repoUser.findOne(x).getLastName());
            user.setId(repoUser.findOne(x).getId());
            recipients.add(user.getId());
        });
        if (recipients.contains(fromUser)) {
            toUsersList.add(repoMessage.findOne(toMessage).getFrom());
            MessageDTO messageDTO = new MessageDTO(fromUser, toUsersList, message, LocalDateTime.now(), toMessage);
            repoMessage.save(messageDTO);
            notifyObservers(new MessageChangeEvent(ChangeEventType.ADD, messageDTO));
        } else
            throw new ValidationException("The user did not receive the message!");

    }

    /**
     * @param toMessage - id-ul mesajului la care se raspunde
     * @param fromUser  - user-ul care da replyAll
     * @param message   - mesajul pe care il trimite
     */
    public void replyToAll(Long toMessage, Long fromUser, String message) {
        if (repoMessage.findOne(toMessage) == null)
            throw new ValidationException("The message do not exist!");
        if (repoUser.findOne(fromUser) == null)
            throw new ValidationException("The user do not exist!");
        if (message.length() == 0)
            throw new ValidationException("The message is empty!");

        List<Long> recipients = new ArrayList<>();

        repoMessage.findOne(toMessage).getTo().forEach(x -> {
            if (!fromUser.equals(x)) {
                User user = new User(repoUser.findOne(x).getFirstName(), repoUser.findOne(x).getLastName());
                user.setId(repoUser.findOne(x).getId());
                recipients.add(user.getId());
            }
        });

        recipients.add(repoMessage.findOne(toMessage).getFrom());

        MessageDTO messageDTO = new MessageDTO(fromUser, recipients, message, LocalDateTime.now(), toMessage);
        repoMessage.save(messageDTO);
    }

    /**
     * get the conversation of two users
     *
     * @param id1
     * @param id2
     * @return
     */
    public List<Message> getConversation(Long id1, Long id2) {
        Iterable<MessageDTO> messages = this.repoMessage.findAll();
        List<MessageDTO> result = new ArrayList<>();
        for (MessageDTO mess : messages) {
            if ((mess.getFrom().equals(id1) && mess.getTo().contains(id2)) || (mess.getFrom().equals(id2) && mess.getTo().contains(id1)))
                result.add(mess);
        }

        result.sort(Comparator.comparing(MessageDTO::getDate).reversed());
        return convertMessages(result);
    }

    /**
     * transform a list of MessageDTOs in a list of Messages
     *
     * @param list
     * @return
     */
    public List<Message> convertMessages(List<MessageDTO> list) {
        List<Message> result = new ArrayList<>();
        list.forEach(x -> {
            List<User> toUsers = new ArrayList<>();
            x.getTo().forEach(y -> {
                toUsers.add(repoUser.findOne(y));
            });
            if (repoMessage.findOne(x.getReply()) == null) {
                Message message = new Message(x.getId(), repoUser.findOne(x.getFrom()), toUsers, x.getMessage(), x.getDate());
                result.add(message);
            } else {
                Message message = new Message(x.getId(), repoUser.findOne(x.getFrom()), toUsers, x.getMessage(), x.getDate(), result.get(result.size() - 1));
                result.add(message);
            }
        });
        return result;
    }

    public List<MessageDTO> getMessagesByDate(LocalDateTime startDate, LocalDateTime endDate, Long loggedUser) {
        Iterable<MessageDTO> messages = repoMessage.findAll();
        List<MessageDTO> messageDTOS = new ArrayList<>();
        List<Long> tos = new ArrayList<>();
        tos.add(loggedUser);

        messages.forEach(x -> {
            Long to = x.getTo().get(0);
            if (to.equals(loggedUser)) {
                MessageDTO message = new MessageDTO(x.getFrom(), tos, x.getMessage(), x.getDate(), x.getReply());
                messageDTOS.add(message);
            }
        });

        Predicate<MessageDTO> isAfter = x -> x.getDate().isAfter(startDate);
        Predicate<MessageDTO> isBefore = x -> x.getDate().isBefore(endDate);
        Predicate<MessageDTO> isEqual = x -> x.getDate().isEqual(endDate);
        Predicate<MessageDTO> isFinal = isAfter.and(isBefore).or(isEqual);

        List<MessageDTO> messageDTOS1 = messageDTOS.stream().filter(isFinal).collect(Collectors.toList());

        return messageDTOS1;
    }

    public List<MessageDTO> getMessagesFromAFriend(LocalDateTime startDate, LocalDateTime endDate, Long loggedUser, Long fromUser) {
        Iterable<MessageDTO> messages = repoMessage.findAll();
        List<MessageDTO> messageDTOS = new ArrayList<>();
        List<Long> tos = new ArrayList<>();
        tos.add(loggedUser);

        messages.forEach(x -> {
            Long to = x.getTo().get(0);
            Long from = x.getFrom();
            if (to.equals(loggedUser) && from.equals(fromUser)) {
                MessageDTO message = new MessageDTO(x.getFrom(), tos, x.getMessage(), x.getDate(), x.getReply());
                messageDTOS.add(message);
            }
        });

        Predicate<MessageDTO> isAfter = x -> x.getDate().isAfter(startDate);
        Predicate<MessageDTO> isBefore = x -> x.getDate().isBefore(endDate);
        Predicate<MessageDTO> isEqual = x -> x.getDate().isEqual(endDate);
        Predicate<MessageDTO> isFinal = isAfter.and(isBefore).or(isEqual);

        List<MessageDTO> messageDTOS1 = messageDTOS.stream().filter(isFinal).collect(Collectors.toList());

        return messageDTOS1;
    }

    /**
     * print a conversation
     *
     * @param conv
     */
    public void listConversation(List<Message> conv) {
        conv.forEach(x -> {
            System.out.println(x.getFrom().getFirstName() + " " + x.getFrom().getLastName() + " sent " + x.getMessage());
        });
    }

    private List<Observer> observers = new ArrayList<>();

    @Override
    public void addObserver(Observer<MessageChangeEvent> e) {
        observers.add(e);
    }

    @Override
    public void notifyObservers(MessageChangeEvent t) {
        observers.forEach(x -> x.update(t));
    }

    public List<Message> getConversationGroup(Long from, List<Long> groupConversation) {
        Iterable<MessageDTO> messages = this.repoMessage.findAll();
        List<MessageDTO> result = new ArrayList<>();
        for (MessageDTO mess : messages) {
            if ((mess.getFrom().equals(from) && mess.getTo().containsAll(groupConversation)) || (groupConversation.contains(mess.getFrom()) && mess.getTo().contains(from)))
                result.add(mess);
        }
        result.sort(Comparator.comparing(MessageDTO::getDate));

        return convertMessages(result);
    }

    public void saveGroup(Group group) {
        repoGroup.save(group);
    }

    public List<Group> getGroups() {
        Iterable<Group> groupIterable = repoGroup.findAll();
        List<Group> groups = new ArrayList<>();
        groupIterable.forEach(groups::add);
        return groups;
    }

    @Override
    public void removeObserver(Observer<MessageChangeEvent> e) {
    }

    private Pageable pageable;
    private int size = 1;
    private int page = 0;

    public void setPageSize(int size) {
        this.size = size;
    }

    //    public void setPageable(Pageable pageable) {
//        this.pageable = pageable;
//    }

    public List<Group> myGroups(Long id) {
        Iterable<Group> groupIterable = repoGroup.findAll();
        List<Group> groupList = new ArrayList<>();
        groupIterable.forEach(groupList::add);

        List<Group> myGroups = new ArrayList<>();

        Predicate<Group> myGroup = x -> x.getMembers().contains(id);
        groupList.stream().filter(myGroup).forEach(myGroups::add);
        Collections.reverse(myGroups);
        return myGroups;
    }

    public List<Group> getGroupsOnPage(int leftLimit, int rightLimit, Long id) {
        Iterable<Group> groupIterable = repoGroup.findAll();
        List<Group> groups = myGroups(id);
        return groups.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
    }

    public List<Group> filterName(String string, Long id) {
        Iterable<Group> groupIterable = repoGroup.findAll();
        List<Group> groupList = new ArrayList<>();
        groupIterable.forEach(groupList::add);

        Predicate<Group> name = x -> x.getName().contains(string);
        Predicate<Group> inGroup = x -> x.getMembers().contains(id);
        Predicate<Group> filter = name.and(inGroup);

        List<Group> myGroups = new ArrayList<>();
        groupList.stream().filter(filter).forEach(myGroups::add);

        return myGroups;

    }

    public List<Group> getSearchingGroupsOnPage(int leftLimit, int rightLimit, Long id, String string) {

        List<Group> groups = filterName(string, id);
        return groups.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
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


    public List<FriendshipDTO> getMyFriendsWithMessages(Long id) {
        List<FriendshipDTO> friendslist = getFriendships(id);
        List<FriendshipDTO> friendshipDTOS = new ArrayList<>();
        friendslist.forEach(x -> {
            if (getConversation(id, x.getUser().getId()).size() > 0)
                friendshipDTOS.add(x);
        });
        return friendshipDTOS;
    }

    public List<FriendshipDTO> getMyConversationPage(int leftLimit, int rightLimit, Long id) {
        List<FriendshipDTO> friendslist = getMyFriendsWithMessages(id);
        return friendslist.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
    }

    public List<Message> getMyMessagesOnPage(int leftLimit, int rightLimit, Long id1, Long id2) {
        List<Message> messages = getConversation(id1, id2);
        return messages.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
    }

    public Group find_group(Long id){
        Group group = repoGroup.findOne(id);
        return group;
    }
    public List<Message> getGroupMessagesOnPage(int leftLimit, int rightLimit, List<Message> messages) {
        messages.sort(Comparator.comparing(Message::getDate).reversed());
        return messages.stream().skip(leftLimit)
                .limit(rightLimit)
                .collect(Collectors.toList());
    }
}

