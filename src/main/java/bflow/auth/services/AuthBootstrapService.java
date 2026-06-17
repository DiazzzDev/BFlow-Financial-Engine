package bflow.auth.services;

import bflow.auth.entities.User;

public interface AuthBootstrapService {

    void bootstrap(User user);

}