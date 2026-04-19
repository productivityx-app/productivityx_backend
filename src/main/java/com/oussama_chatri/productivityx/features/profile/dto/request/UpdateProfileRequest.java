package com.oussama_chatri.productivityx.features.profile.dto.request;

import com.oussama_chatri.productivityx.core.enums.Language;
import com.oussama_chatri.productivityx.core.enums.Theme;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "First name must not exceed 50 characters.")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters.")
    private String lastName;

    @Size(max = 500, message = "Bio must not exceed 500 characters.")
    private String bio;

    @Size(max = 50, message = "Timezone must not exceed 50 characters.")
    private String timezone;

    private Language language;

    private Theme theme;
}
