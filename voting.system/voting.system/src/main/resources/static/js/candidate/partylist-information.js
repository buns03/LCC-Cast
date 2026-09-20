/* =========================================================
   LCCAST — CANDIDATE PARTYLIST/DEPARTMENT INFORMATION
========================================================= */

document.addEventListener("DOMContentLoaded", () => {
  loadGroupInfo();
});

function escapeHTML(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function renderSkeleton() {
  const container = document.getElementById("groupInfoContainer");
  if (!container) return;

  container.innerHTML = `
    <div class="candidate-group-skeleton">
      <div class="skeleton skeleton-group-poster"></div>
      <div class="skeleton skeleton-group-title"></div>
      <div class="skeleton skeleton-group-line"></div>
      <div class="skeleton skeleton-group-line short"></div>
    </div>
  `;
}

async function loadGroupInfo() {
  renderSkeleton();

  try {
    const res = await fetch("/candidate/api/group-info");
    const data = await res.json();

    if (!res.ok) {
      renderNoGroup(data.message || "No partylist or department information found.");
      return;
    }

    renderGroupInfo(data);
  } catch (err) {
    console.error(err);
    renderNoGroup("Something went wrong while loading your group information.");
  }
}

function renderNoGroup(message) {
  const container = document.getElementById("groupInfoContainer");
  if (!container) return;

  container.innerHTML = `
    <div class="election-instructions">
      <h3>No Group Information</h3>
      <p>${escapeHTML(message)}</p>
    </div>
  `;
}

function renderGroupInfo(data) {
  const container = document.getElementById("groupInfoContainer");
  if (!container) return;

  const groupLabel = data.groupType === "PARTYLIST" ? "Partylist" : "Department";

  container.innerHTML = `
    <div class="candidate-group-card">
      <div class="candidate-group-poster">
        <img src="${data.posterUrl || "/images/campaign-placeholder.png"}" alt="${escapeHTML(data.name)} poster" />
      </div>

      <div class="candidate-group-header">
        <div class="candidate-group-logo">
          <img src="${data.logoUrl || "/images/default-avatar.png"}" alt="${escapeHTML(data.name)} logo" />
        </div>

        <div>
          <span class="candidate-group-type">${groupLabel}</span>
          <h2>${escapeHTML(data.name)}</h2>
          <p>${escapeHTML(data.schoolYear || "")}</p>
        </div>
      </div>

      <div class="candidate-group-description">
        <h3>Description</h3>
        <p id="groupDescriptionText">${escapeHTML(data.description || "No description provided yet.")}</p>
      </div>
    </div>

    ${data.canEdit ? renderEditForm(data) : ""}

    <div class="candidate-group-members">
      <h3><i class="bi bi-people"></i> Members</h3>

      <div class="candidate-group-member-grid">
        ${data.members.map((m) => `
          <article class="candidate-group-member-card">
            <img src="${m.photoUrl || "/images/default-avatar.png"}" alt="${escapeHTML(m.fullName)}" />
            <div>
              <strong>${escapeHTML(m.fullName)}</strong>
              <span>${escapeHTML(m.position || "Member")}</span>
            </div>
          </article>
        `).join("")}
      </div>
    </div>
  `;

  if (data.canEdit) {
    initializeEditForm();
  }
}

function renderEditForm(data) {
  return `
    <div class="candidate-group-edit-card">
      <div class="candidate-group-edit-header">
        <h3><i class="bi bi-pencil-square"></i> Edit Group Information</h3>
        <p>As President, you can update this group's poster, logo, and description.</p>
      </div>

      <form id="groupEditForm" novalidate>
        <div class="form-group">
          <label for="groupDescriptionInput">Description</label>
          <textarea id="groupDescriptionInput">${escapeHTML(data.description || "")}</textarea>
        </div>

        <div class="candidate-group-edit-images">
          <div class="candidate-image-row">
            <div class="candidate-image-preview candidate-image-preview-wide" id="groupPosterPreviewWrapper">
              <img id="groupPosterPreview" src="${data.posterUrl || "/images/campaign-placeholder.png"}" alt="Poster preview" />
            </div>
            <div class="candidate-image-actions">
              <input type="file" id="groupPosterInput" accept="image/png,image/jpeg,image/webp" hidden />
              <button type="button" class="secondary-btn" id="changeGroupPosterBtn">
                <i class="bi bi-upload"></i> Change Poster
              </button>
            </div>
          </div>

          <div class="candidate-image-row">
            <div class="candidate-image-preview candidate-image-preview-round" id="groupLogoPreviewWrapper">
              <img id="groupLogoPreview" src="${data.logoUrl || "/images/default-avatar.png"}" alt="Logo preview" />
            </div>
            <div class="candidate-image-actions">
              <input type="file" id="groupLogoInput" accept="image/png,image/jpeg,image/webp" hidden />
              <button type="button" class="secondary-btn" id="changeGroupLogoBtn">
                <i class="bi bi-upload"></i> Change Logo
              </button>
            </div>
          </div>
        </div>

        <div class="settings-form-actions">
          <button type="submit" class="save-btn">
            <i class="bi bi-check-lg"></i> Save Changes
          </button>
        </div>
      </form>
    </div>
  `;
}

let selectedPosterFile = null;
let selectedLogoFile = null;

function initializeEditForm() {
  selectedPosterFile = null;
  selectedLogoFile = null;

  const posterInput = document.getElementById("groupPosterInput");
  const logoInput = document.getElementById("groupLogoInput");

  document.getElementById("changeGroupPosterBtn")?.addEventListener("click", () => posterInput?.click());
  document.getElementById("changeGroupLogoBtn")?.addEventListener("click", () => logoInput?.click());

  posterInput?.addEventListener("change", () => {
    selectedPosterFile = posterInput.files?.[0] || null;
    if (selectedPosterFile) {
      const reader = new FileReader();
      reader.onload = (e) => { document.getElementById("groupPosterPreview").src = e.target.result; };
      reader.readAsDataURL(selectedPosterFile);
    }
  });

  logoInput?.addEventListener("change", () => {
    selectedLogoFile = logoInput.files?.[0] || null;
    if (selectedLogoFile) {
      const reader = new FileReader();
      reader.onload = (e) => { document.getElementById("groupLogoPreview").src = e.target.result; };
      reader.readAsDataURL(selectedLogoFile);
    }
  });

  document.getElementById("groupEditForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();

    const description = document.getElementById("groupDescriptionInput").value.trim();

    const formData = new FormData();
    formData.append("description", description);
    if (selectedPosterFile) formData.append("poster", selectedPosterFile);
    if (selectedLogoFile) formData.append("logo", selectedLogoFile);

    showActionLoading("Saving Changes...", "Please wait while group information is being updated.");

    try {
      const res = await fetch("/candidate/api/group-info", {
        method: "POST",
        body: formData,
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || "Could not update group information.");
      }

      showSuccessToast("Updated", data.message || "Group information updated successfully.");
      await loadGroupInfo();
    } catch (err) {
      showSuccessToast("Update Failed", err.message || "Could not update group information.", true);
    } finally {
      hideActionLoading();
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