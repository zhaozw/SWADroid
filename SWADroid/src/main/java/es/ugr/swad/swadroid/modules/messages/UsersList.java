package es.ugr.swad.swadroid.modules.messages;

/**
 * Created by Romilgildo on 24/07/2016.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import es.ugr.swad.swadroid.model.UserFilter;

/**
 * Users list
 */
public class UsersList {
    private List<UserFilter> users = new ArrayList();

    public void saveUser(UserFilter user) {
        users.add(user);
    }

    public List<UserFilter> getUsers() {

        return users;
    }
}
