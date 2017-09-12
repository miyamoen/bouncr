package net.unit8.bouncr.web.controller.admin;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import enkan.component.doma2.DomaProvider;
import enkan.data.HttpResponse;
import enkan.security.UserPrincipal;
import kotowari.component.TemplateEngine;
import kotowari.routing.UrlRewriter;
import net.unit8.bouncr.authz.UserPermissionPrincipal;
import net.unit8.bouncr.util.RandomUtils;
import net.unit8.bouncr.web.dao.PasswordCredentialDao;
import net.unit8.bouncr.web.dao.UserDao;
import net.unit8.bouncr.web.entity.User;
import net.unit8.bouncr.web.form.UserForm;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Random;

import static enkan.util.HttpResponseUtils.RedirectStatusCode.SEE_OTHER;

/**
 * A controller for user actions.
 *
 * @author kawasima
 */
public class UserController {
    @Inject
    private TemplateEngine templateEngine;

    @Inject
    private DomaProvider daoProvider;

    @Inject
    private BeansConverter beansConverter;

    @RolesAllowed("LIST_USERS")
    public HttpResponse list(UserPrincipal principal) {
        UserDao userDao = daoProvider.getDao(UserDao.class);
        List<User> users;
        users = userDao.selectAll();

        return templateEngine.render("admin/user/list",
                "users", users);
    }

    public List<User> search(Parameters params) {
        String word = params.get("q");
        UserDao userDao = daoProvider.getDao(UserDao.class);
        return userDao.selectForIncrementalSearch(word + "%");
    }

    @RolesAllowed("CREATE_USER")
    public HttpResponse newUser() {
        UserForm user = new UserForm();
        return templateEngine.render("admin/user/new",
                "user", user);
    }

    @RolesAllowed("CREATE_USER")
    @Transactional
    public HttpResponse create(UserForm form) {
        if (form.hasErrors()) {
            return templateEngine.render("admin/user/new",
                    "user", form);
        }
        User user = beansConverter.createFrom(form, User.class);
        user.setWriteProtected(false);
        UserDao userDao = daoProvider.getDao(UserDao.class);
        userDao.insert(user);

        PasswordCredentialDao passwordCredentialDao = daoProvider.getDao(PasswordCredentialDao.class);
        Random random = new Random();
        passwordCredentialDao.insert(
                user.getId(),
                form.getPassword(),
                RandomUtils.generateRandomString(random, 16));


        return UrlRewriter.redirect(UserController.class, "list", SEE_OTHER);
    }

    @RolesAllowed("MODIFY_USER")
    public HttpResponse edit(Parameters params) {
        UserDao userDao = daoProvider.getDao(UserDao.class);
        User user = userDao.selectById(params.getLong("id"));
        UserForm form = beansConverter.createFrom(user, UserForm.class);
        return templateEngine.render("admin/user/edit",
                "user", form,
                "userId", user.getId());
    }

    @RolesAllowed("MODIFY_USER")
    @Transactional
    public HttpResponse update(UserForm form, Parameters params) {
        if (form.hasErrors()) {
            return templateEngine.render("admin/user/edit",
                    "user", form);
        }
        UserDao userDao = daoProvider.getDao(UserDao.class);
        User user = userDao.selectById(params.getLong("id"));
        beansConverter.copy(form, user);
        userDao.update(user);

        PasswordCredentialDao passwordCredentialDao = daoProvider.getDao(PasswordCredentialDao.class);
        Random random = new Random();
        passwordCredentialDao.update(
                user.getId(),
                form.getPassword(),
                RandomUtils.generateRandomString(random, 16));

        return UrlRewriter.redirect(UserController.class, "list", SEE_OTHER);
    }
}