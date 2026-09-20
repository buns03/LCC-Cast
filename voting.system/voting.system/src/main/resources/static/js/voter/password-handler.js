document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("changePasswordForm");

  const passwordInput = document.getElementById("password");
  const confirmPasswordInput =
    document.getElementById("confirmPassword");

  const passwordError = document.getElementById("passwordError");
  const confirmPasswordError =
    document.getElementById("confirmPasswordError");

  const loadingModal = document.getElementById("actionLoadingModal");

  if (!form) return;

  /*
   * Username comes from Thymeleaf:
   *
   * <span th:text="${username}">Username</span>
   *
   * We store it in a data attribute so JavaScript
   * can compare the new password against it.
   */
  const username =
    document.querySelector(".change-password-header span")?.textContent.trim() || "";

  function clearErrors() {
    passwordError.textContent = "";
    confirmPasswordError.textContent = "";

    passwordInput.classList.remove("input-error");
    confirmPasswordInput.classList.remove("input-error");
  }

  function showPasswordError(message) {
    passwordError.textContent = message;
    passwordInput.classList.add("input-error");
  }

  function showConfirmPasswordError(message) {
    confirmPasswordError.textContent = message;
    confirmPasswordInput.classList.add("input-error");
  }

  function showLoadingModal() {
    if (!loadingModal) return;

    loadingModal.classList.add("active");
    loadingModal.setAttribute("aria-hidden", "false");

    document.body.classList.add("modal-loading");
  }

  function validatePassword() {
    const password = passwordInput.value.trim();
    const confirmPassword = confirmPasswordInput.value.trim();

    clearErrors();

    let valid = true;

    /*
     * Password cannot be the same as username.
     * Case-insensitive comparison is used.
     */
    if (
      password &&
      username &&
      password.toLowerCase() === username.toLowerCase()
    ) {
      showPasswordError(
        "Password cannot be the same as your username."
      );

      valid = false;
    }

    /*
     * Check password confirmation.
     */
    if (
      password &&
      confirmPassword &&
      password !== confirmPassword
    ) {
      showConfirmPasswordError(
        "Passwords do not match."
      );

      valid = false;
    }

    return valid;
  }

  /*
   * Submit
   */
  form.addEventListener("submit", (event) => {
    event.preventDefault();

    const password = passwordInput.value.trim();
    const confirmPassword = confirmPasswordInput.value.trim();

    clearErrors();

    let valid = true;

    /*
     * Empty password
     */
    if (!password) {
      showPasswordError("Please enter a new password.");
      valid = false;
    }

    /*
     * Empty confirmation
     */
    if (!confirmPassword) {
      showConfirmPasswordError(
        "Please confirm your new password."
      );
      valid = false;
    }

    /*
     * Password cannot equal username
     */
    if (
      password &&
      username &&
      password.toLowerCase() === username.toLowerCase()
    ) {
      showPasswordError(
        "Password cannot be the same as your username."
      );
      valid = false;
    }

    /*
     * Passwords must match
     */
    if (
      password &&
      confirmPassword &&
      password !== confirmPassword
    ) {
      showConfirmPasswordError(
        "Passwords do not match."
      );
      valid = false;
    }

    /*
     * Stop here if validation failed.
     */
    if (!valid) return;

    /*
     * Everything is valid.
     * Allow the form to submit to Spring Boot.
     */
    showLoadingModal();

    form.submit();
  });

  /*
   * Remove password error while typing.
   */
  passwordInput.addEventListener("input", () => {
    passwordError.textContent = "";
    passwordInput.classList.remove("input-error");

    /*
     * Re-check confirmation if it already has a value.
     */
    if (confirmPasswordInput.value) {
      if (
        passwordInput.value !== confirmPasswordInput.value
      ) {
        showConfirmPasswordError(
          "Passwords do not match."
        );
      } else {
        confirmPasswordError.textContent = "";
        confirmPasswordInput.classList.remove("input-error");
      }
    }
  });

  /*
   * Check confirmation while typing.
   */
  confirmPasswordInput.addEventListener("input", () => {
    confirmPasswordError.textContent = "";
    confirmPasswordInput.classList.remove("input-error");

    if (
      confirmPasswordInput.value &&
      passwordInput.value !== confirmPasswordInput.value
    ) {
      showConfirmPasswordError(
        "Passwords do not match."
      );
    }
  });
});
