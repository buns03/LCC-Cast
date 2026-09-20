/* =========================================================
   LCCAST — CANDIDATE PERSONAL INFORMATION
========================================================= */

document.addEventListener("DOMContentLoaded", () => {
  loadPersonalInfo();
  initializeImageUpload("photo", "photoInput", "changePhotoBtn", "photoPreview", "photoError");
  initializeImageUpload("background", "backgroundInput", "changeBackgroundBtn", "backgroundPreview", "backgroundError");
  initializeImageUpload("campaign", "campaignInput", "changeCampaignBtn", "campaignPreview", "campaignError");
});

async function loadPersonalInfo() {
  try {
    const res = await fetch("/candidate/api/personal-info");
    if (!res.ok) throw new Error("Failed to load personal information.");

    const data = await res.json();

    if (data.photoUrl) {
      document.getElementById("photoPreview").src = data.photoUrl;
      const topbarAvatar = document.getElementById("topbarAvatarImg");
      if (topbarAvatar) topbarAvatar.src = data.photoUrl;
    }

    if (data.backgroundUrl) {
      document.getElementById("backgroundPreview").src = data.backgroundUrl;
    }

    if (data.campaignUrl) {
      document.getElementById("campaignPreview").src = data.campaignUrl;
    }
  } catch (err) {
    console.error(err);
  }
}

function initializeImageUpload(type, inputId, buttonId, previewId, errorId) {
  const input = document.getElementById(inputId);
  const button = document.getElementById(buttonId);
  const preview = document.getElementById(previewId);
  const error = document.getElementById(errorId);

  if (!input || !button || !preview) return;

  button.addEventListener("click", () => input.click());

  input.addEventListener("change", async () => {
    const file = input.files?.[0];
    if (!file) return;

    if (error) error.textContent = "";

    // Local preview immediately
    const reader = new FileReader();
    reader.onload = (e) => { preview.src = e.target.result; };
    reader.readAsDataURL(file);

    const formData = new FormData();
    formData.append("file", file);

    showActionLoading("Uploading...", "Please wait while your image is being uploaded.");

    try {
      const res = await fetch(`/candidate/api/personal-info/${type}`, {
        method: "POST",
        body: formData,
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || "Upload failed.");
      }

      preview.src = data.url;

      if (type === "photo") {
        const topbarAvatar = document.getElementById("topbarAvatarImg");
        if (topbarAvatar) topbarAvatar.src = data.url;
      }

      showSuccessToast("Updated", data.message || "Image updated successfully.");
    } catch (err) {
      if (error) error.textContent = err.message;
      showSuccessToast("Upload Failed", err.message || "Could not upload image.", true);
    } finally {
      hideActionLoading();
      input.value = "";
    }
  });
}

/* =========================================================
   LOADING / TOAST HELPERS
========================================================= */

function showActionLoading(title, message) {
  document.getElementById("actionLoadingTitle").textContent = title;
  document.getElementById("actionLoadingMessage").textContent = message;
  document.body.classList.add("modal-loading");
  document.getElementById("actionLoadingModal")?.classList.add("show");
}

function hideActionLoading() {
  document.body.classList.remove("modal-loading");
  document.getElementById("actionLoadingModal")?.classList.remove("show");
}

let toastTimeout = null;

function showSuccessToast(title, message, isError = false) {
  const toast = document.getElementById("successToast");
  if (!toast) return;

  const icon = toast.querySelector(".success-toast-icon i");
  document.getElementById("successToastTitle").textContent = title;
  document.getElementById("successToastMessage").textContent = message;

  if (icon) {
    icon.classList.toggle("bi-check-lg", !isError);
    icon.classList.toggle("bi-x-lg", isError);
  }

  toast.classList.toggle("toast-error", isError);
  toast.classList.add("show");

  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => toast.classList.remove("show"), 4000);
}

document.getElementById("successToastClose")?.addEventListener("click", () => {
  document.getElementById("successToast")?.classList.remove("show");
});