package net.unit8.bouncr.web.form;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @author kawasima
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UserForm extends FormBase {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String account;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 256)
    private String password;
}
