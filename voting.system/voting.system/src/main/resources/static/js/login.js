/* =========================================================
   LCCAST
   LOGIN PAGE
========================================================= */

document.addEventListener("DOMContentLoaded", () => {
  initializeActionLoadingModal();

  initializeLogin();

  initializeForgotPassword();

  initializePasswordToggles();

//  applyBranding();
});

//async function applyBranding() {
//  try {
//    const res = await fetch("/api/public/branding");
//    if (!res.ok) return;
//
//    const branding = await res.json();
//    const nameEl = document.getElementById("loginSystemName");
//    const taglineEl = document.getElementById("loginTagline");
//    const logoCircle = document.getElementById("loginLogoCircle");
//
//    if (nameEl && branding.systemName) {
//      nameEl.textContent = branding.systemName;
//    }
//
//    if (taglineEl && branding.description) {
//      taglineEl.textContent = branding.description;
//    }
//
//    if (logoCircle && branding.hasLogo) {
//      logoCircle.innerHTML = `<img src="/api/public/branding/logo?t=${Date.now()}" alt="Logo" style="width:100%;height:100%;object-fit:cover;border-radius:inherit;" />`;
//    }
//  } catch (err) {
//    console.error("Failed to load branding:", err);
//  }
//}

function initializePasswordToggles() {
  const toggles = document.querySelectorAll(".password-toggle");

  toggles.forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("data-target");
      const input = document.getElementById(targetId);
      const icon = btn.querySelector("i");

      if (!input) return;

      const isHidden = input.type === "password";
      input.type = isHidden ? "text" : "password";

      if (icon) {
        icon.classList.toggle("bi-eye", !isHidden);
        icon.classList.toggle("bi-eye-slash", isHidden);
      }

      btn.setAttribute("aria-label", isHidden ? "Hide password" : "Show password");
    });
  });
}

/* =========================================================
   ACTION LOADING MODAL
========================================================= */

function initializeActionLoadingModal() {
  const modal = document.getElementById("actionLoadingModal");

  if (!modal) {
    return;
  }

  window.showActionLoading = (
    title = "Please Wait",
    message = "Processing your request...",
  ) => {
    const titleElement = document.getElementById("actionLoadingTitle");

    const messageElement = document.getElementById("actionLoadingMessage");

    if (titleElement) {
      titleElement.textContent = title;
    }

    if (messageElement) {
      messageElement.textContent = message;
    }

    modal.classList.add("show");

    modal.setAttribute("aria-hidden", "false");

    document.body.classList.add("modal-loading");
  };

  window.hideActionLoading = () => {
    modal.classList.remove("show");

    modal.setAttribute("aria-hidden", "true");

    document.body.classList.remove("modal-loading");
  };
}

/* =========================================================
   LOGIN
========================================================= */

function initializeLogin() {
  const loginForm = document.getElementById("loginForm");

  const schoolIdInput = document.getElementById("schoolId");

  const passwordInput = document.getElementById("password");

  const schoolIdError = document.getElementById("schoolIdError");

  const passwordError = document.getElementById("passwordError");

  if (!loginForm) {
    return;
  }

  /*
   * Clear old client-side error when user edits
   * the School ID field.
   */

  schoolIdInput.addEventListener("input", () => {
    clearFieldError(schoolIdInput, schoolIdError);

    hideServerError();
  });

  /*
   * Clear old client-side error when user edits
   * the password field.
   */

  passwordInput.addEventListener("input", () => {
    clearFieldError(passwordInput, passwordError);

    hideServerError();
  });

  /*
   * Login validation.
   *
   * IMPORTANT:
   * This does NOT check whether the credentials
   * are correct.
   *
   * The backend must determine that.
   */

  loginForm.addEventListener("submit", (event) => {
    const schoolId = schoolIdInput.value.trim();

    const password = passwordInput.value;

    let isValid = true;

    clearFieldError(schoolIdInput, schoolIdError);

    clearFieldError(passwordInput, passwordError);

    /* -----------------------------------------
           SCHOOL ID
        ----------------------------------------- */

    if (!schoolId) {
      showFieldError(schoolIdInput, schoolIdError, "School ID is required.");

      isValid = false;
    }

    /* -----------------------------------------
           PASSWORD
        ----------------------------------------- */

    if (!password) {
      showFieldError(passwordInput, passwordError, "Password is required.");

      isValid = false;
    }

    /*
     * Stop submission only when the fields
     * themselves are invalid.
     */

    if (!isValid) {
      event.preventDefault();

      return;
    }

    /* -----------------------------------------
   SHOW LOGIN LOADING
----------------------------------------- */

    showActionLoading("Logging In", "Verifying your School ID and password...");

    /*
     * DO NOT clear either field here.
     *
     * The form is submitted normally to:
     *
     * POST /login
     */
  });
}

/* =========================================================
   FORGOT PASSWORD
========================================================= */

function initializeForgotPassword() {
  const forgotPasswordBtn = document.getElementById("forgotPasswordBtn");

  const modal = document.getElementById("forgotPasswordModal");

  const closeForgotModal = document.getElementById("closeForgotModal");

  const verifyEmailBtn = document.getElementById("verifyEmailBtn");

  const verifyOtpBtn = document.getElementById("verifyOtpBtn");

  const resetPasswordBtn = document.getElementById("resetPasswordBtn");

  const returnToLoginBtn = document.getElementById("returnToLoginBtn");

  if (!modal) {
    return;
  }

  /* -----------------------------------------
       OPEN MODAL
    ----------------------------------------- */

  forgotPasswordBtn.addEventListener("click", () => {
    openForgotPasswordModal();
  });

  /* -----------------------------------------
       CLOSE / CANCEL PROCESS
    ----------------------------------------- */

  closeForgotModal.addEventListener("click", () => {
    closeForgotPasswordModal();
  });

  /*
   * Clicking the dark overlay also cancels
   * the entire forgot-password process.
   */

  modal.addEventListener("click", (event) => {
    if (event.target === modal) {
      closeForgotPasswordModal();
    }
  });

  /*
   * Escape key also cancels the process.
   */

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.classList.contains("show")) {
      closeForgotPasswordModal();
    }
  });

  /* -----------------------------------------
       EMAIL
    ----------------------------------------- */

  verifyEmailBtn.addEventListener("click", verifyEmail);

  /* -----------------------------------------
       OTP
    ----------------------------------------- */

  verifyOtpBtn.addEventListener("click", verifyOtp);

  /* -----------------------------------------
       PASSWORD RESET
    ----------------------------------------- */

  resetPasswordBtn.addEventListener("click", resetPassword);

  /* -----------------------------------------
       RETURN TO LOGIN
    ----------------------------------------- */

  returnToLoginBtn.addEventListener("click", () => {
    closeForgotPasswordModal();
  });

  initializeForgotPasswordInputs();
}

/* =========================================================
   FORGOT PASSWORD INPUT EVENTS
========================================================= */

function initializeForgotPasswordInputs() {
  const registeredEmail = document.getElementById("registeredEmail");

  const otp = document.getElementById("otp");

  const newPassword = document.getElementById("newPassword");

  const confirmPassword = document.getElementById("confirmPassword");

  registeredEmail.addEventListener("input", () => {
    clearFieldError(
      registeredEmail,
      document.getElementById("registeredEmailError"),
    );
  });

  otp.addEventListener("input", () => {
    /*
     * Allow only numeric characters.
     *
     * This is input formatting, not
     * OTP verification.
     */

    otp.value = otp.value.replace(/\D/g, "");

    clearFieldError(otp, document.getElementById("otpError"));
  });

  newPassword.addEventListener("input", () => {
    clearFieldError(newPassword, document.getElementById("newPasswordError"));
  });

  confirmPassword.addEventListener("input", () => {
    clearFieldError(
      confirmPassword,
      document.getElementById("confirmPasswordError"),
    );
  });
}

/* =========================================================
   VERIFY EMAIL
========================================================= */

async function verifyEmail() {
  const registeredEmail = document.getElementById("registeredEmail");
  const errorElement = document.getElementById("registeredEmailError");
  const email = registeredEmail.value.trim();

  clearFieldError(registeredEmail, errorElement);

  if (!email) {
    showFieldError(registeredEmail, errorElement, "Email is required.");
    return;
  }

  if (!isValidEmail(email)) {
    showFieldError(registeredEmail, errorElement, "Enter a valid email address.");
    return;
  }

  showActionLoading("Verifying Email", "Checking your registered email address...");

  try {
    const response = await fetch("/forgot-password/verify-email", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email }),
    });

    const result = await parseJsonResponse(response);

    if (!response.ok || !result.success) {
      showFieldError(
        registeredEmail,
        errorElement,
        result.message || "The email could not be verified.",
      );
      return;
    }

    /* NEW: show which email the OTP was sent to, masked. */
    const otpSentTo = document.getElementById("otpSentTo");
    if (otpSentTo && result.maskedEmail) {
      otpSentTo.textContent = result.maskedEmail;
    }

    showResetStep("otpStep");
  } catch (error) {
    showFieldError(
      registeredEmail,
      errorElement,
      "Unable to verify the email right now.",
    );
  } finally {
    hideActionLoading();
  }
}

/* =========================================================
   VERIFY OTP
========================================================= */

async function verifyOtp() {
  const otpInput = document.getElementById("otp");

  const errorElement = document.getElementById("otpError");

  const registeredEmail = document.getElementById("registeredEmail");

  const otp = otpInput.value.trim();

  const email = registeredEmail.value.trim();

  clearFieldError(otpInput, errorElement);

  /* -----------------------------------------
       EMPTY
    ----------------------------------------- */

  if (!otp) {
    showFieldError(otpInput, errorElement, "OTP is required.");

    return;
  }

  /* -----------------------------------------
       OTP FORMAT
    ----------------------------------------- */

  if (!/^\d{6}$/.test(otp)) {
    showFieldError(
      otpInput,
      errorElement,
      "OTP must contain exactly 6 digits.",
    );

    return;
  }

  showActionLoading("Verifying OTP", "Checking your verification code...");

  try {
    const response = await fetch("/forgot-password/verify-otp", {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({
        email: email,
        otp: otp,
      }),
    });

    const result = await parseJsonResponse(response);

    if (!response.ok || !result.success) {
      showFieldError(
        otpInput,
        errorElement,
        result.message || "Invalid or expired OTP.",
      );

      return;
    }

    /*
     * Backend confirmed the OTP.
     */

    showResetStep("newPasswordStep");
  } catch (error) {
    showFieldError(
      otpInput,
      errorElement,
      "Unable to verify the OTP right now.",
    );
  } finally {
    hideActionLoading();
  }
}

/* =========================================================
   RESET PASSWORD
========================================================= */

async function resetPassword() {
  const newPassword = document.getElementById("newPassword");

  const confirmPassword = document.getElementById("confirmPassword");

  const newPasswordError = document.getElementById("newPasswordError");

  const confirmPasswordError = document.getElementById("confirmPasswordError");

  const email = document.getElementById("registeredEmail").value.trim();

  const otp = document.getElementById("otp").value.trim();

  clearFieldError(newPassword, newPasswordError);

  clearFieldError(confirmPassword, confirmPasswordError);

  let isValid = true;

  /* -----------------------------------------
       NEW PASSWORD
    ----------------------------------------- */

  if (!newPassword.value) {
    showFieldError(newPassword, newPasswordError, "New password is required.");

    isValid = false;
  } else if (!isValidPassword(newPassword.value)) {
    showFieldError(
      newPassword,
      newPasswordError,
      "Password must be at least 8 characters.",
    );

    isValid = false;
  }

  /* -----------------------------------------
       CONFIRM PASSWORD
    ----------------------------------------- */

  if (!confirmPassword.value) {
    showFieldError(
      confirmPassword,
      confirmPasswordError,
      "Please confirm your new password.",
    );

    isValid = false;
  } else if (confirmPassword.value !== newPassword.value) {
    showFieldError(
      confirmPassword,
      confirmPasswordError,
      "Passwords do not match.",
    );

    isValid = false;
  }

  if (!isValid) {
    return;
  }

  showActionLoading("Changing Password", "Saving your new password...");

  try {
    /*
     * BACKEND INTEGRATION POINT
     *
     * POST /forgot-password/reset
     *
     * {
     *     email: email,
     *     otp: otp,
     *     newPassword: newPassword
     * }
     */

    const response = await fetch("/forgot-password/reset", {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({
        email: email,

        otp: otp,

        newPassword: newPassword.value,
      }),
    });

    const result = await parseJsonResponse(response);

    if (!response.ok || !result.success) {
      showFieldError(
        newPassword,
        newPasswordError,
        result.message || "Unable to change the password.",
      );

      return;
    }

    /*
     * Password successfully changed.
     */

    showResetStep("resetSuccessStep");
  } catch (error) {
    showFieldError(
      newPassword,
      newPasswordError,
      "Unable to change the password right now.",
    );
  } finally {
    hideActionLoading();
  }
}

/* =========================================================
   OPEN FORGOT PASSWORD MODAL
========================================================= */

function openForgotPasswordModal() {
  const modal = document.getElementById("forgotPasswordModal");

  /*
   * Always start at email verification.
   */

  showResetStep("resetEmailStep");

  modal.classList.add("show");

  modal.setAttribute("aria-hidden", "false");

  document.body.classList.add("modal-open");

  /*
   * Focus email field.
   */

  setTimeout(() => {
    const email = document.getElementById("registeredEmail");

    email.focus();
  }, 100);
}

/* =========================================================
   CLOSE FORGOT PASSWORD MODAL
========================================================= */

function closeForgotPasswordModal() {
  const modal = document.getElementById("forgotPasswordModal");

  /*
   * Closing the modal means the user
   * cancelled the entire process.
   *
   * Therefore it is safe to clear the
   * forgot-password fields.
   */

  resetForgotPasswordForm();

  modal.classList.remove("show");

  modal.setAttribute("aria-hidden", "true");

  document.body.classList.remove("modal-open");

  /*
   * Return focus to Forgot Password button.
   */

  const forgotPasswordBtn = document.getElementById("forgotPasswordBtn");

  forgotPasswordBtn.focus();
}

/* =========================================================
   RESET FORGOT PASSWORD FORM
========================================================= */

function resetForgotPasswordForm() {
  const fields = ["registeredEmail", "otp", "newPassword", "confirmPassword"];

  fields.forEach((id) => {
    const input = document.getElementById(id);
    if (input) {
      input.value = "";
      input.classList.remove("input-error", "input-success");
    }
  });

  const errors = [
    "registeredEmailError",
    "otpError",
    "newPasswordError",
    "confirmPasswordError",
  ];

  errors.forEach((id) => {
    const error = document.getElementById(id);
    if (error) {
      error.textContent = "";
    }
  });

  /* NEW: reset the masked-email placeholder */
  const otpSentTo = document.getElementById("otpSentTo");
  if (otpSentTo) {
    otpSentTo.textContent = "your registered email";
  }

  showResetStep("resetEmailStep");
}


/* =========================================================
   SHOW RESET STEP
========================================================= */

function showResetStep(stepId) {
  const steps = document.querySelectorAll(".reset-step");

  steps.forEach((step) => {
    step.classList.remove("active");
  });

  const target = document.getElementById(stepId);

  if (target) {
    target.classList.add("active");
  }
}

/* =========================================================
   FIELD ERROR
========================================================= */

function showFieldError(input, errorElement, message) {
  if (input) {
    input.classList.add("input-error");

    input.classList.remove("input-success");
  }

  if (errorElement) {
    errorElement.textContent = message;
  }
}

/* =========================================================
   CLEAR FIELD ERROR
========================================================= */

function clearFieldError(input, errorElement) {
  if (input) {
    input.classList.remove("input-error");
  }

  if (errorElement) {
    errorElement.textContent = "";
  }
}

/* =========================================================
   SERVER ERROR
========================================================= */

function hideServerError() {
  const serverError = document.getElementById("loginServerError");

  if (serverError) {
    serverError.style.display = "none";
  }
}

/* =========================================================
   EMAIL VALIDATION
========================================================= */

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/* =========================================================
   PASSWORD VALIDATION
========================================================= */

function isValidPassword(password) {
  return password.length >= 8;
}

/* =========================================================
   JSON RESPONSE
========================================================= */

async function parseJsonResponse(response) {
  const contentType = response.headers.get("content-type");

  if (contentType && contentType.includes("application/json")) {
    return await response.json();
  }

  /*
   * If the backend returns something other
   * than JSON, don't crash the application.
   */

  return {};
}
