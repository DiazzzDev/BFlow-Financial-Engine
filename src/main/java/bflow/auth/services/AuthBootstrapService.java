package bflow.auth.services;

import bflow.auth.entities.User;

public interface AuthBootstrapService {

    /**
     * Creates the default resources required for a newly created user.
     *
     * @param user user being initialized
     */
    void bootstrap(User user);

}
