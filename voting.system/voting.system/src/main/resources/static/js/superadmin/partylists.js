/* LCCAST - Partylists Page */

/* =========================================================
   GLOBAL ACTION LOADING MODAL
========================================================= */

const PARTYLIST_API = "/superadmin/api/partylists";

function showActionLoading(title, message) {
  const modal = document.getElementById("actionLoadingModal");
  const titleElement = document.getElementById("actionLoadingTitle");
  const messageElement = document.getElementById("actionLoadingMessage");

  if (!modal) {
    console.error("actionLoadingModal not found.");
    return;
  }

  if (titleElement) {
    titleElement.textContent = title;
  }

  if (messageElement) {
    messageElement.textContent = message;
  }

  modal.classList.add("show");
  document.body.classList.add("modal-loading");
}

function hideActionLoading() {
  const modal = document.getElementById("actionLoadingModal");

  if (!modal) {
    return;
  }

  modal.classList.remove("show");
  document.body.classList.remove("modal-loading");
}

document.addEventListener("DOMContentLoaded", async () => {
    initializePartylistsTabs();
    initializePartylistsCards();
    initializeCreateMembers();
    initializeEditForms();
    initializeDeleteDiscardModals();
    initializeSelectionModalButtons();
    initializePartylistsFileUploads();
    initializeExistingMemberFiles();
    initializeCampusFilter();
    initializeSuccessToast();
    initializeValidationBindings();

    await loadCampuses();
    await loadExistingPartylists();
});

const DEFAULT_POSITIONS = [
    "President", "Vice President", "Secretary", "Treasurer",
    "Auditor", "PRO Internal", "PRO External"
];

let pendingDeleteCard = null;
let pendingArchiveCard = null;
let pendingSaveAction = null;
let successToastTimeout = null;
let discardToastTimeout = null;

async function loadPartylists() {
    const list = document.querySelector("#existingPartylists .partylists-list");

    if (!list) return;

    try {
        showActionLoading(
            "Loading Partylists",
            "Please wait while partylists are retrieved."
        );

        const response = await fetch(PARTYLIST_API, {
            method: "GET",
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(`Failed to load partylists: ${response.status}`);
        }

        const partylists = await response.json();

        list.innerHTML = "";

        for (const partylist of partylists) {

            const card = document.createElement("article");

            card.className = "partylists-item";
            card.dataset.id = partylist.id || "";
            card.dataset.campus = partylist.campus?.id || "";

            const campusName = partylist.campus?.name || "";

            card.innerHTML = `
                <div class="partylists-item-header">

                    <div class="partylists-item-title">

                        <div class="partylists-logo-small">
                            ${
                                partylist.posterLogoUrl
                                    ? `
                                        <img
                                            class="partylists-logo-image"
                                            src="${esc(partylist.posterLogoUrl)}"
                                            alt="${esc(partylist.name || "")} Logo">
                                      `
                                    : `
                                        <span class="partylists-logo-initials">
                                            ${esc(getPartylistsInitials(partylist.name))}
                                        </span>
                                      `
                            }
                        </div>

                        <div class="partylists-title-text">
                            <h3>${esc(partylist.name || "")}</h3>

                            <span class="partylists-description">
                                ${esc(partylist.description || "")}
                            </span>
                        </div>

                    </div>

                    <div class="partylists-header-right">

                        <div class="partylists-campus">
                            <i class="bi bi-building"></i>
                            <span>${esc(campusName)}</span>
                        </div>

                        <div class="partylists-controls">

                            <button
                                type="button"
                                class="icon-btn edit-partylists"
                                title="Edit">
                                <i class="bi bi-pencil"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn archive-partylists"
                                title="Archive">
                                <i class="bi bi-archive"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn delete-partylists"
                                title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn expand-partylists"
                                title="Expand">
                                <i class="bi bi-chevron-down"></i>
                            </button>

                        </div>
                    </div>
                </div>

                <section class="partylists-details">

                    <div class="partylists-info">

                        <div class="partylists-expanded-content">

                            <div class="partylists-poster-preview">

                                <div class="partylists-preview-title">
                                    <span>Poster</span>
                                </div>

                                <div class="partylists-file-preview existing-poster-preview">
                                    ${
                                        partylist.posterImageUrl
                                            ? `
                                                <img
                                                    src="${esc(partylist.posterImageUrl)}"
                                                    alt="${esc(partylist.name || "")} Poster">
                                              `
                                            : ""
                                    }
                                </div>

                            </div>

                            <div class="partylists-expanded-details">

                                <div class="partylists-info-row">
                                    <label>Campus:</label>
                                    <span class="existing-partylists-campus">
                                        ${esc(campusName)}
                                    </span>
                                </div>

                                <div class="partylists-info-row">
                                    <label>School Year:</label>
                                    <span>
                                        ${esc(partylist.schoolYear || "")}
                                    </span>
                                </div>

                                <div class="partylists-info-row members-info-row">
                                    <label>Members:</label>
                                    <div class="existing-member-list">
                                        Loading members...
                                    </div>
                                </div>

                            </div>

                        </div>

                    </div>

                </section>
            `;

            list.appendChild(card);

            bindPartylistsCard(card);
            initializePartylistsFileUploads(card);
            bindEditForm(card);

            await loadPartylistMembers(card, partylist.id);
        }

        refreshExisting();

    } catch (error) {

        console.error("Error loading partylists:", error);

        list.innerHTML = `
            <div class="empty-state">
                <span>Unable to load partylists.</span>
            </div>
        `;

    } finally {
        hideActionLoading();
    }
}

async function loadExistingPartylists() {

    const list = document.querySelector("#existingPartylists .partylists-list");

    if (!list) return;

    try {

        const response = await fetch(`${PARTYLIST_API}`, {
            method: "GET",
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(`Failed to load partylists: ${response.status}`);
        }

        const partylists = await response.json();

        list.innerHTML = "";

        if (!Array.isArray(partylists) || partylists.length === 0) {
            list.innerHTML = `
                <div class="empty-state">
                    No active partylists found.
                </div>
            `;
            refreshExisting();
            return;
        }

        for (const partylist of partylists) {

            const card = document.createElement("article");

            card.className = "partylists-item";
            card.dataset.id = partylist.id || "";
            card.dataset.campus = partylist.campus?.id || "";

            const campusName = partylist.campus?.name || "";

            card.innerHTML = `
                <div class="partylists-item-header">

                    <div class="partylists-item-title">

                        <div class="partylists-logo-small">

                            ${
                                partylist.posterLogoUrl
                                    ? `
                                        <img
                                            class="partylists-logo-image"
                                            src="${esc(partylist.posterLogoUrl)}"
                                            alt="${esc(partylist.name || "")} Logo"
                                        >
                                      `
                                    : `
                                        <span class="partylists-logo-initials">
                                            ${esc(getPartylistsInitials(partylist.name))}
                                        </span>
                                      `
                            }

                        </div>

                        <div class="partylists-title-text">

                            <h3>${esc(partylist.name || "")}</h3>

                            <span class="partylists-description">
                                ${esc(partylist.description || "")}
                            </span>

                        </div>

                    </div>

                    <div class="partylists-header-right">

                        <div class="partylists-campus">
                            <i class="bi bi-building"></i>
                            <span>${esc(campusName)}</span>
                        </div>

                        <div class="partylists-controls">

                            <button
                                type="button"
                                class="icon-btn edit-partylists"
                                title="Edit">
                                <i class="bi bi-pencil"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn archive-partylists"
                                title="Archive">
                                <i class="bi bi-archive"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn delete-partylists"
                                title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>

                            <button
                                type="button"
                                class="icon-btn expand-partylists"
                                title="Expand">
                                <i class="bi bi-chevron-down"></i>
                            </button>

                        </div>

                    </div>

                </div>

                <section class="partylists-details">

                    <div class="partylists-info">

                        <div class="partylists-expanded-content">

                            <div class="partylists-poster-preview">

                                <div class="partylists-preview-title">
                                    <span>Poster</span>
                                </div>

                                <div class="partylists-file-preview existing-poster-preview">

                                    ${
                                        partylist.posterImageUrl
                                            ? `
                                                <img
                                                    src="${esc(partylist.posterImageUrl)}"
                                                    alt="${esc(partylist.name || "")} Poster"
                                                >
                                              `
                                            : `
                                                <div class="file-preview-empty">
                                                    No poster uploaded.
                                                </div>
                                              `
                                    }

                                </div>

                            </div>

                            <div class="partylists-expanded-details">

                                <div class="partylists-info-row">
                                    <label>Campus:</label>
                                    <span class="existing-partylists-campus">
                                        ${esc(campusName)}
                                    </span>
                                </div>

                                <div class="partylists-info-row">
                                    <label>School Year:</label>
                                    <span>
                                        ${esc(partylist.schoolYear || "")}
                                    </span>
                                </div>

                                <div class="partylists-info-row members-info-row">

                                    <label>Members:</label>

                                    <div class="existing-member-list">
                                        Loading members...
                                    </div>

                                </div>

                            </div>

                        </div>

                    </div>

                </section>
            `;

            list.appendChild(card);

            bindPartylistsCard(card);
            initializePartylistsFileUploads(card);
            bindEditForm(card);

            await loadPartylistMembers(card, partylist.id);
        }

        refreshExisting();

    } catch (error) {

        console.error("Error loading partylists:", error);

        list.innerHTML = `
            <div class="empty-state">
                Unable to load partylists.
            </div>
        `;
    }
}

async function loadPartylistMembers(card, partylistId) {

    const memberList = card.querySelector(".existing-member-list");

    if (!memberList || !partylistId) return;

    try {

        const response = await fetch(
            `${PARTYLIST_API}/${partylistId}/members`,
            {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Failed to load partylist members.");
        }

        const members = await response.json();

        memberList.innerHTML = "";

        if (!Array.isArray(members) || members.length === 0) {

            memberList.innerHTML = `
                <div class="existing-member empty-member">
                    No members.
                </div>
            `;

            return;
        }

        members.forEach(member => {

            const fullName = [
                member.firstName,
                member.middleName,
                member.lastName
            ]
                .filter(Boolean)
                .join(" ");

            const element = document.createElement("div");

            element.className = "existing-member";

            element.dataset.studentId = member.studentId || "";
            element.dataset.campaign = member.campaignImageUrl || "";
            element.dataset.background = member.backgroundImageUrl || "";
            element.dataset.photo = member.photoImageUrl || "";

            element.innerHTML = `
                <span>${esc(member.studentId || "")}</span>

                <span>${esc(fullName)}</span>

                <span>${esc(member.position || "")}</span>
            `;

            memberList.appendChild(element);
        });

    } catch (error) {

        console.error("Failed to load members:", error);

        memberList.innerHTML = `
            <div class="existing-member empty-member">
                Unable to load members.
            </div>
        `;
    }
}

async function loadCampuses() {

    const campusSelect = document.getElementById("partylistsCampus");

    if (!campusSelect) return;

    try {

        const response = await fetch(
            `${PARTYLIST_API}/campuses`,
            {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Failed to load campuses.");
        }

        const campuses = await response.json();

        campusSelect.innerHTML = `
            <option value="">Select Campus</option>
        `;

        campuses.forEach(campus => {

            if (!campus.id) return;

            const option = document.createElement("option");

            option.value = campus.id;
            option.textContent = campus.name || "";

            campusSelect.appendChild(option);
        });

    } catch (error) {

        console.error("Failed to load campuses:", error);

        campusSelect.innerHTML = `
            <option value="">Unable to load campuses</option>
        `;
    }
}

async function createPartylist(form) {

    const campusId =
        document.getElementById("partylistsCampus")?.value;

    const members = [
        ...document.querySelectorAll("#createMembersList .member-row")
    ].map(collectMemberData);

    const payload = {

        name: document.getElementById("partylistsName")?.value.trim() || "",

        description:
            document.getElementById("partylistsDescription")?.value.trim() || "",

        schoolYear:
            document.getElementById("partylistsSchoolYear")?.value.trim() || "",

        campus: {
            id: campusId
        },

        status: "ACTIVE",

        posterImageUrl:
            document.getElementById("partylistsPoster")?.dataset.url || null,

        posterLogoUrl:
            document.getElementById("partylistsLogo")?.dataset.url || null,

        members: members.map(member => ({
            studentId: member.studentId,
            lastName: member.lastName,
            firstName: member.firstName,
            middleName: member.middleName || null,
            position: member.position,

            campaignImageUrl: member.campaign || null,
            backgroundImageUrl: member.background || null,
            photoImageUrl: member.photo || null
        }))
    };

    try {

        showActionLoading(
            "Creating Partylist",
            "Saving partylist information..."
        );

        const response = await fetch(PARTYLIST_API, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {

            const message = await response.text();

            throw new Error(
                message || `Create failed: ${response.status}`
            );
        }

        await response.json();

        hideActionLoading();

        await loadExistingPartylists();

        return true;

    } catch (error) {

        console.error("Create partylist failed:", error);

        hideActionLoading();

        alert(error.message || "Failed to create partylist.");

        return false;
    }
}

async function updatePartylist(id, card) {

    const editForm = card.querySelector(".edit-partylists-form");

    if (!editForm) {
        console.error("Edit form not found.");
        return false;
    }

    const campusSelect = editForm.querySelector(
        ".partylists-campus-select"
    );

    const memberRows = editForm.querySelectorAll(".member-row");

    const members = [...memberRows].map(collectMemberData);

    const payload = {

        name:
            editForm.querySelector(".partylists-name")?.value.trim() || "",

        description:
            editForm.querySelector(".partylists-description-input")
                ?.value.trim() || "",

        schoolYear:
            editForm.querySelector(".partylists-school-year")
                ?.value.trim() || "",

        campus: campusSelect?.value
            ? { id: campusSelect.value }
            : null,

        posterImageUrl:
            editForm.querySelector(".partylists-poster-input")
                ?.dataset.url || null,

        posterLogoUrl:
            editForm.querySelector(".partylists-logo-input")
                ?.dataset.url || null,

        members: members.map(member => ({
            studentId: member.studentId,
            lastName: member.lastName,
            firstName: member.firstName,
            middleName: member.middleName || null,
            position: member.position,

            campaignImageUrl: member.campaign || null,
            backgroundImageUrl: member.background || null,
            photoImageUrl: member.photo || null
        }))
    };

    try {

        showActionLoading(
            "Updating Partylist",
            "Saving partylist changes..."
        );

        const response = await fetch(
            `${PARTYLIST_API}/${id}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(payload)
            }
        );

        if (!response.ok) {

            const message = await response.text();

            throw new Error(
                message || `Update failed: ${response.status}`
            );
        }

        await response.json();

        hideActionLoading();

        await loadExistingPartylists();

        return true;

    } catch (error) {

        console.error("Update partylist failed:", error);

        hideActionLoading();

        alert(error.message || "Failed to update partylist.");

        return false;
    }
}

async function archivePartylist(id) {

    try {

        showActionLoading(
            "Archiving Partylist",
            "Please wait..."
        );

        const response = await fetch(
            `${PARTYLIST_API}/${id}/archive`,
            {
                method: "PUT",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Failed to archive partylist.");
        }

        hideActionLoading();

        await loadExistingPartylists();

        return true;

    } catch (error) {

        console.error("Archive failed:", error);

        hideActionLoading();

        alert(error.message || "Failed to archive partylist.");

        return false;
    }
}

async function deletePartylist(id) {

    try {

        showActionLoading(
            "Deleting Partylist",
            "Please wait..."
        );

        const response = await fetch(
            `${PARTYLIST_API}/${id}`,
            {
                method: "DELETE",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Failed to delete partylist.");
        }

        hideActionLoading();

        await loadExistingPartylists();

        return true;

    } catch (error) {

        console.error("Delete failed:", error);

        hideActionLoading();

        alert(error.message || "Failed to delete partylist.");

        return false;
    }
}

async function restorePartylist(id) {

    try {

        showActionLoading(
            "Restoring Partylist",
            "Please wait..."
        );

        const response = await fetch(
            `${PARTYLIST_API}/${id}/restore`,
            {
                method: "PUT",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Failed to restore partylist.");
        }

        hideActionLoading();

        await loadExistingPartylists();

        return true;

    } catch (error) {

        console.error("Restore failed:", error);

        hideActionLoading();

        alert(error.message || "Failed to restore partylist.");

        return false;
    }
}
// Accordions
function initializeAccordions() {
    const accordions = document.querySelectorAll(".accordion");

    accordions.forEach(accordion => {
        const header = accordion.querySelector(":scope > .accordion-header");
        const content = accordion.querySelector(":scope > .accordion-content");
        if (!header || !content) return;

        accordion.classList.contains("active")
            ? setAccordionHeight(accordion, content)
            : (content.style.maxHeight = "0px");

        header.addEventListener("click", e => {
            e.preventDefault();
            e.stopPropagation();

            const isOpen = accordion.classList.contains("active");

            if (isOpen) {
                accordion.classList.remove("active");
                content.style.maxHeight = "0px";
                setArrow(accordion, false);
                return;
            }

            accordions.forEach(other => {
                if (other === accordion) return;
                other.classList.remove("active");
                const otherContent = other.querySelector(":scope > .accordion-content");
                if (otherContent) otherContent.style.maxHeight = "0px";
                setArrow(other, false);
            });

            accordion.classList.add("active");
            setArrow(accordion, true);
            requestAnimationFrame(() => setAccordionHeight(accordion, content));
        });

        const observer = new ResizeObserver(() => {
            if (!accordion.classList.contains("active")) return;
            content.style.maxHeight = content.scrollHeight + "px";
        });

        observer.observe(content);
        accordion._resizeObserver = observer;
    });
}

function setAccordionHeight(accordion, content) {
    content.style.maxHeight = accordion.classList.contains("active")
        ? content.scrollHeight + "px"
        : "0px";
}

function setArrow(accordion, open) {
    const arrow = accordion.querySelector(":scope > .accordion-header .accordion-arrow");
    if (!arrow) return;
    arrow.className = open ? "bi bi-chevron-up accordion-arrow" : "bi bi-chevron-down accordion-arrow";
}

// Partylists Initials
function getPartylistsInitials(name) {
    if (!name) return "";
    const words = name.trim().split(/\s+/);
    return words.length === 1
        ? words[0].substring(0, 2).toUpperCase()
        : words.map(word => word[0]).join("").toUpperCase();
}

// Update Partylists Logo
function updatePartylistsLogo(card) {
    const logoImage = card.querySelector(".partylists-logo-image");
    const initials = card.querySelector(".partylists-logo-initials");
    const title = card.querySelector(".partylists-item-title h3");
    if (!logoImage || !initials || !title) return;

    const partyName = title.textContent.trim();
    const hasLogo = logoImage.src && logoImage.getAttribute("src") &&
        !logoImage.src.endsWith("/images/default-partylists.png");

    if (hasLogo) {
        logoImage.style.display = "block";
        initials.style.display = "none";
        return;
    }

    logoImage.style.display = "none";
    initials.textContent = getPartylistsInitials(partyName);
    initials.style.display = "flex";
}

// Partylists Cards
function initializePartylistsCards() {
    document.querySelectorAll("#existingPartylists .partylists-item").forEach(bindPartylistsCard);
}

function bindPartylistsCard(card) {
    if (!card || card.dataset.bound === "true") return;
    card.dataset.bound = "true";
    updatePartylistsLogo(card);

    const expand = card.querySelector(".expand-partylists");
    const edit = card.querySelector(".edit-partylists");
    const remove = card.querySelector(".delete-partylists");
    const archive = card.querySelector(".archive-partylists");

    // Expand
    expand?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();

        const isOpen = card.classList.contains("show");

        allPartylistsCards().forEach(other => {
            if (other === card) return;
            other.classList.remove("show", "editing");
            const icon = other.querySelector(".expand-partylists i");
            if (icon) icon.className = "bi bi-chevron-down";
        });

        card.classList.toggle("show", !isOpen);

        const icon = expand.querySelector("i");
        if (icon) icon.className = card.classList.contains("show") ? "bi bi-chevron-up" : "bi bi-chevron-down";

        refreshExisting();
    });

    // Edit
    edit?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();

        const isEditing = card.classList.contains("editing");

        allPartylistsCards().forEach(other => {
            if (other === card) return;
            other.classList.remove("editing", "show");
            const otherIcon = other.querySelector(".expand-partylists i");
            if (otherIcon) otherIcon.className = "bi bi-chevron-down";
        });

        if (isEditing) {
            card.classList.remove("editing");
        } else {
            // Enter edit mode - hide the normal expanded details
            card.classList.remove("show");
            card.classList.add("editing");

            // Keep expand icon pointing down since details are collapsed
            const icon = card.querySelector(".expand-partylists i");
            if (icon) icon.className = "bi bi-chevron-down";

            initializeEditMembers(card);
        }

        refreshExisting();
    });

    // Delete
    remove?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();
        openDeleteModal(card);
    });

    // Archive
    archive?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();
        openArchiveModal(card);
    });
}

function allPartylistsCards() {
    return document.querySelectorAll("#existingPartylists .partylists-item");
}

// Create Members
function initializeCreateMembers() {
    const list = document.getElementById("createMembersList");
    const add = document.getElementById("addMemberBtn");
    if (!list || !add) return;

    DEFAULT_POSITIONS.forEach(position => addMemberRow(list, { position }, false));

    add.addEventListener("click", () => {
        addMemberRow(list, { position: "Member" }, true);
        updateRemoveButtons(list);
    });
}

// Add Member Row
function addMemberRow(list, member = {}, removable = true) {
    const row = document.createElement("div");
    row.className = removable ? "member-row can-remove" : "member-row";

    const position = member.position || "Member";
    const existingPhoto = member.photo || "";
    const existingBackground = member.background || "";
    const isOtherPosition = !DEFAULT_POSITIONS.includes(position) && position !== "Member";

    row.innerHTML = `
        <div class="member-field-group">
            <input type="text" class="member-student-id" placeholder="Student No. / ID"
                value="${esc(member.studentId || "")}" autocomplete="off" inputmode="text">
            <small class="inline-error member-student-id-error"></small>
        </div>

        <div class="member-field-group">
            <input type="text" class="member-last-name" placeholder="Last Name" value="${esc(member.lastName || "")}">
            <small class="inline-error member-last-name-error"></small>
        </div>

        <div class="member-field-group">
            <input type="text" class="member-first-name" placeholder="First Name" value="${esc(member.firstName || "")}">
            <small class="inline-error member-first-name-error"></small>
        </div>

        <div class="member-field-group">
            <input type="text" class="member-middle-name" placeholder="Middle Name" value="${esc(member.middleName || "")}">
        </div>

        <div class="member-field-group">
            <div class="position-wrapper">
                <select class="member-position">${positionOptions(position)}</select>
                <input type="text" class="other-position-input" placeholder="Enter position"
                    value="${isOtherPosition ? esc(position) : ""}" ${isOtherPosition ? "" : "hidden"}>
                <small class="inline-error member-position-error"></small>
            </div>
        </div>

        <input type="file" class="campaign-input" accept="image/*" hidden>

        <button type="button" class="campaign-btn" title="Upload Campaign / Platform">
            <span>Campaign</span>
        </button>

        <input type="file" class="background-input"
       accept="application/pdf,image/*" hidden>

        <button type="button" class="background-btn" title="Upload Background">
            <span>Background</span>
        </button>

        <input type="file" class="photo-input" accept="image/*" hidden>

        <button type="button" class="photo-btn" title="Upload member photo">
            <i class="bi bi-person-bounding-box"></i>
            <span>Photo</span>
        </button>


        <button type="button" class="remove-member" title="Remove member">
            <i class="bi bi-x-lg"></i>
        </button>

        <div class="member-file-preview"></div>
    `;

    // Student ID / key
    const studentIdInput = row.querySelector(".member-student-id");

    studentIdInput?.addEventListener("input", () => {
        const error = row.querySelector(".member-student-id-error");
        if (studentIdInput.value.trim()) {
            error?.classList.remove("show");
            if (error) error.textContent = "";
            studentIdInput.classList.remove("input-error");
        }
    });

    studentIdInput?.addEventListener("change", async () => {
        const studentId = studentIdInput.value.trim();

        if (!studentId) return;

        const lastNameInput = row.querySelector(".member-last-name");
        const firstNameInput = row.querySelector(".member-first-name");
        const middleNameInput = row.querySelector(".member-middle-name");

        try {
            showActionLoading(
                "Finding Student",
                "Please wait while we retrieve the student information."
            );

            const student = await lookupStudentById(studentId);

            if (!student) {
                hideActionLoading();

                showInlineError(
                    studentIdInput,
                    "Student ID was not found."
                );

                return;
            }

            lastNameInput.value = student.lastName || "";
            firstNameInput.value = student.firstName || "";
            middleNameInput.value = student.middleName || "";

            clearInlineError(studentIdInput);

            clearInlineError(lastNameInput);
            clearInlineError(firstNameInput);

        } catch (error) {
            console.error("Student lookup failed:", error);

            showInlineError(
                studentIdInput,
                "Unable to retrieve student information."
            );

        } finally {
            hideActionLoading();
        }
    });

    // Position
    const positionSelect = row.querySelector(".member-position");
    const otherInput = row.querySelector(".other-position-input");

    positionSelect.addEventListener("change", () => {
        if (positionSelect.value === "Others") {
            otherInput.hidden = false;
            otherInput.focus();
        } else {
            otherInput.hidden = true;
            otherInput.value = "";
        }
    });

    // Campaign / Platform Image
    const campaignFile = row.querySelector(".campaign-input");
    const campaignButton = row.querySelector(".campaign-btn");

    campaignButton?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();

        if (row._campaignFile || row.dataset.campaign) {
            toggleMemberFilePreview(row, "campaign");
            return;
        }

        campaignFile?.click();
    });

    campaignFile?.addEventListener("change", () => {
        const file = campaignFile.files?.[0];
        if (!file) return;

        if (!file.type.startsWith("image/")) {
            alert("Please select an image file.");
            campaignFile.value = "";
            return;
        }

        row._campaignFile = file;
        row.dataset.campaign = URL.createObjectURL(file);

        campaignButton.classList.add("has-campaign");
        campaignButton.innerHTML = `
        <i class="bi bi-check-lg"></i>
        <span>Campaign</span>
    `;

        showMemberFilePreview(row, "campaign");
        refreshExisting();
    });

    // Background Image
    const backgroundFile = row.querySelector(".background-input");
    const backgroundButton = row.querySelector(".background-btn");

    backgroundButton?.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();

        if (row._backgroundFile || row.dataset.background) {
            toggleMemberFilePreview(row, "background");
            return;
        }

        backgroundFile?.click();
    });

    backgroundFile?.addEventListener("change", async () => {
        const file = backgroundFile.files?.[0];
        if (!file) return;

        if (!file.type.startsWith("image/") && file.type !== "application/pdf") {
            alert("Please select an image or PDF file.");
            backgroundFile.value = "";
            return;
        }

        row._backgroundFile = file;
        row.dataset.background = URL.createObjectURL(file);

        backgroundButton.classList.add("has-background");
        backgroundButton.innerHTML = `
        <i class="bi bi-check-lg"></i>
        <span>Background</span>
    `;

        showMemberFilePreview(row, "background");
        refreshExisting();

        try {
            await processBackground(file, row);
        } catch (error) {
            console.warn("Background auto-reading failed:", error);
        }
    });

    // Photo
    const photoFile = row.querySelector(".photo-input");
    const photoButton = row.querySelector(".photo-btn");

    photoButton.addEventListener("click", e => {
        e.preventDefault();
        e.stopPropagation();

        // Existing photo -> show preview, otherwise open picker
        if (row._photoFile || row.dataset.photo) {
            toggleMemberFilePreview(row, "photo");
            return;
        }
        photoFile.click();
    });

    photoFile.addEventListener("change", () => {
        const file = photoFile.files?.[0];
        if (!file) return;

        if (!file.type.startsWith("image/")) {
            alert("Please select an image file.");
            photoFile.value = "";
            return;
        }

        row._photoFile = file;
        row.dataset.photo = URL.createObjectURL(file);

        photoButton.classList.add("has-photo");
        photoButton.innerHTML = `<i class="bi bi-check-lg"></i><span>Photo</span>`;

        showMemberFilePreview(row, "photo");
        refreshExisting();
    });



    // Remove
    row.querySelector(".remove-member").addEventListener("click", () => {
        row.remove();
        updateRemoveButtons(list);
        refreshAccordion(list.closest(".accordion"));
    });

    list.appendChild(row);
    const existingCampaign = member.campaign || "";


    if (existingCampaign) {
        row.dataset.campaign = existingCampaign;
        row._campaignFile = null;

        campaignButton?.classList.add("has-campaign");

        if (campaignButton) {
            campaignButton.innerHTML = `
            <i class="bi bi-check-lg"></i>
            <span>Campaign</span>
        `;
        }
    }


    if (existingBackground) {
        row.dataset.background = existingBackground;
        row._backgroundFile = null;

        backgroundButton?.classList.add("has-background");

        if (backgroundButton) {
            backgroundButton.innerHTML = `
            <i class="bi bi-check-lg"></i>
            <span>Background</span>
        `;
        }
    }

    if (existingPhoto) {
        row.dataset.photo = existingPhoto;
        row._photoFile = null;

        photoButton?.classList.add("has-photo");

        if (photoButton) {
            photoButton.innerHTML =
                `<i class="bi bi-check-lg"></i><span>Photo</span>`;
        }
    }
}

// Student Lookup (backend-ready, not connected yet)
async function lookupStudentById(studentId) {
    if (!studentId) return null;

    try {
        const response = await fetch(
            `/superadmin/api/voters/student/${encodeURIComponent(studentId)}`
        );

        if (!response.ok) {
            return null;
        }

        return await response.json();

    } catch (error) {
        console.error("Student lookup failed:", error);
        return null;
    }
}

// Member File Preview
function toggleMemberFilePreview(row, type) {
    const preview = row.querySelector(".member-file-preview");
    if (!preview) return;

    const isOpen = preview.classList.contains("show");

    document.querySelectorAll(".member-file-preview.show").forEach(element => {
        element.classList.remove("show");
    });

    if (isOpen) {
        preview.classList.remove("show");
        return;
    }

    showMemberFilePreview(row, type);
}

function showMemberFilePreview(row, type) {
    const preview = row.querySelector(".member-file-preview");
    if (!preview) return;

    let file = null;

    if (type === "campaign") {
        file = row._campaignFile || row.dataset.campaign || null;
    } else if (type === "background") {
        file = row._backgroundFile || row.dataset.background || null;
    } else if (type === "photo") {
        file = row._photoFile || row.dataset.photo || null;
    }


    const fileUrl = file instanceof File ? URL.createObjectURL(file) : file;

    if (type === "campaign" || type === "background" || type === "photo") {
        preview.innerHTML = `
            <div class="file-preview-header">
                <strong>
                    ${type === "campaign"
                ? "Campaign / Platform"
                : type === "background"
                    ? "Background"
                    : "Photo Preview"}
                </strong>
                <button type="button" class="file-preview-close" title="Close"><i class="bi bi-x-lg"></i></button>
            </div>
            <div class="file-preview-body photo-preview-body">
                <img
                    src="${esc(fileUrl)}"
                    alt="${type === "campaign"
                ? "Campaign / Platform"
                : type === "background"
                    ? "Background"
                    : "Member Photo"}"
                >
            </div>
            <div class="file-preview-actions">
                <button type="button" class="file-change-btn"><i class="bi bi-arrow-repeat"></i>Change</button>
                <button type="button" class="file-delete-btn"><i class="bi bi-trash"></i>Delete</button>
            </div>
        `;
    } else {
        const fileName = file instanceof File ? file.name : getFileName(file);
        const isPDF = fileName.toLowerCase().endsWith(".pdf");

        preview.innerHTML = `
            <div class="file-preview-header">
                <strong>Resume / COC</strong>
                <button type="button" class="file-preview-close" title="Close"><i class="bi bi-x-lg"></i></button>
            </div>
            <div class="file-preview-filename">
                <i class="bi ${isPDF ? "bi-file-earmark-pdf" : "bi-file-earmark-image"}"></i>
                <span>${esc(fileName)}</span>
            </div>
            <div class="file-preview-body background-preview-body">
                ${isPDF
                ? `<iframe src="${esc(fileUrl)}" title="Resume Preview"></iframe>`
                : `<img src="${esc(fileUrl)}" alt="Resume Preview">`}
            </div>
            <div class="file-preview-actions">
                <button type="button" class="file-change-btn"><i class="bi bi-arrow-repeat"></i>Change</button>
                <button type="button" class="file-delete-btn"><i class="bi bi-trash"></i>Delete</button>
            </div>
        `;
    }

    preview.classList.add("show");

    preview.querySelector(".file-preview-close")?.addEventListener("click", e => {
        e.stopPropagation();
        closeMemberFilePreview(row);
    });

    preview.querySelector(".file-change-btn")?.addEventListener("click", e => {
        e.stopPropagation();
        let input;

        if (type === "photo") {
            input = row.querySelector(".photo-input");
        } else if (type === "campaign") {
            input = row.querySelector(".campaign-input");
        } else if (type === "background") {
            input = row.querySelector(".background-input");
        }

        input?.click();
    });

    preview.querySelector(".file-delete-btn")?.addEventListener("click", e => {
        e.stopPropagation();
        deleteMemberFile(row, type);
    });

    // Wait for render so getBoundingClientRect() has correct dimensions
    requestAnimationFrame(() => positionMemberFilePreview(row, preview));
}

// Edit Forms
function initializeEditForms() {
    document.querySelectorAll("#existingPartylists .partylists-item").forEach(card => bindEditForm(card));

    // Create form
    const form = document.getElementById("createPartylistsForm");

    form?.addEventListener("submit", e => {
        e.preventDefault();
        clearAllInlineErrors();

        const nameInput = document.getElementById("partylistsName");
        const schoolYearInput = document.getElementById("partylistsSchoolYear");
        const campusSelect = document.getElementById("partylistsCampus");
        const memberList = document.getElementById("createMembersList");

        let isValid = true;

        if (!nameInput || !validateRequiredField(nameInput, "Partylists name is required.")) isValid = false;
        if (!schoolYearInput || !validateRequiredField(schoolYearInput, "Partylists school year is required.")) isValid = false;
        if (!campusSelect || !validateRequiredField(campusSelect, "Please select a campus.")) isValid = false;
        if (memberList && !validateMembers(memberList)) isValid = false;

        if (!isValid) return;

        pendingSaveAction = { type: "create", form };
        openModal("savePartylistsModal");
    });

    // Discard
    document.getElementById("discardPartylists")?.addEventListener("click", e => {
        e.preventDefault();
        openDiscardModal();
    });
}



// Binds the edit form for ONE card. Guarded so repeat calls (e.g. after a new
// card is created) don't stack duplicate listeners.
function bindEditForm(card) {
    if (!card || card.dataset.editBound === "true") return;
    card.dataset.editBound = "true";

    const form = card.querySelector(".edit-partylists-form");
    const list = card.querySelector(".edit-members-list");
    const add = card.querySelector(".edit-add-member");
    const cancel = card.querySelector(".cancel-partylists-edit");

    const campusSelect = form?.querySelector(".edit-partylists-campus");
    loadEditCampusOptions(campusSelect);

    if (!form) return;

    // Add member
    add?.addEventListener("click", e => {
        e.preventDefault();
        initializeEditMembers(card);
        addMemberRow(list, { position: "Member" }, true);
        updateRemoveButtons(list);
        refreshExisting();
    });

    // Cancel edit
    cancel?.addEventListener("click", e => {
        e.preventDefault();
        clearAllInlineErrors();
        card.classList.remove("editing", "show");

        const icon = card.querySelector(".expand-partylists i");
        if (icon) icon.className = "bi bi-chevron-down";

        refreshExisting();
    });

    // Save edit
    form.addEventListener("submit", e => {
        e.preventDefault();
        clearAllInlineErrors();

        const nameInput = form.querySelector(".edit-partylists-name-input");
        const schoolYearInput = form.querySelector(".edit-partylists-schoolyear-input");
        const campusSelect = form.querySelector(".edit-partylists-campus");
        const editList = form.querySelector(".edit-members-list");


        let isValid = true;

        if (!nameInput || !validateRequiredField(nameInput, "Partylists name is required.")) isValid = false;
        if (!schoolYearInput || !validateRequiredField(schoolYearInput, "Partylists school year is required.")) isValid = false;
        if (!campusSelect || !validateRequiredField(campusSelect, "Please select a campus.")) isValid = false;
        if (editList && editList.children.length && !validateMembers(editList)) isValid = false;

        if (!isValid) return;

        pendingSaveAction = { type: "edit", card };
        openModal("savePartylistsModal");
    });
}



function createPartylistCard(partylist) {
    const card = document.createElement("article");

    card.className = "partylists-item";
    card.dataset.id = partylist.id;
    card.dataset.campus = partylist.campus?.name || "";
    card.dataset.campusId = partylist.campus?.id || "";

    const members = partylist.members || [];

    card.innerHTML = `
        <div class="partylists-item-header">

            <div class="partylists-item-title">

                <div class="partylists-logo-small">

                    ${
                        partylist.posterLogoUrl
                            ? `<img
                                class="partylists-logo-image"
                                src="${esc(partylist.posterLogoUrl)}"
                                alt="${esc(partylist.name)} Logo"
                              >`
                            : `<img
                                class="partylists-logo-image"
                                src="/images/default-partylists.png"
                                alt="${esc(partylist.name)} Logo"
                              >`
                    }

                    <span class="partylists-logo-initials">
                        ${esc(getPartylistsInitials(partylist.name))}
                    </span>

                </div>

                <div class="partylists-title-text">

                    <h3>${esc(partylist.name)}</h3>

                    <span class="partylists-description">
                        ${esc(partylist.description || "")}
                    </span>

                </div>

            </div>

            <div class="partylists-header-right">

                <div class="partylists-campus">
                    <i class="bi bi-building"></i>
                    <span>${esc(partylist.campus?.name || "")}</span>
                </div>

                <div class="partylists-controls">

                    <button
                        type="button"
                        class="icon-btn edit-partylists"
                        title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>

                    <button
                        type="button"
                        class="icon-btn archive-partylists"
                        title="Archive">
                        <i class="bi bi-archive"></i>
                    </button>

                    <button
                        type="button"
                        class="icon-btn delete-partylists"
                        title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>

                    <button
                        type="button"
                        class="icon-btn expand-partylists"
                        title="Expand">
                        <i class="bi bi-chevron-down"></i>
                    </button>

                </div>

            </div>

        </div>

        <section class="partylists-details">

            <div class="partylists-info">

                <div class="partylists-expanded-content">

                    <div class="partylists-poster-preview">

                        <div class="partylists-preview-title">
                            <span>Poster</span>
                        </div>

                        <div class="partylists-file-preview existing-poster-preview">

                            ${
                                partylist.posterImageUrl
                                    ? `
                                        <img
                                            src="${esc(partylist.posterImageUrl)}"
                                            alt="${esc(partylist.name)} Poster"
                                        >
                                      `
                                    : ""
                            }

                        </div>

                    </div>

                    <div class="partylists-expanded-details">

                        <div class="partylists-info-row">
                            <label>Campus:</label>
                            <span class="existing-partylists-campus">
                                ${esc(partylist.campus?.name || "")}
                            </span>
                        </div>

                        <div class="partylists-info-row">
                            <label>Description:</label>
                            <span>
                                ${esc(partylist.description || "—")}
                            </span>
                        </div>

                        <div class="partylists-info-row">
                            <label>School Year:</label>
                            <span>
                                ${esc(partylist.schoolYear || "—")}
                            </span>
                        </div>

                        <div class="partylists-info-row members-info-row">

                            <label>Members:</label>

                            <div class="existing-member-list">

                                ${members.map(member => `
                                    <div
                                        class="existing-member"
                                        data-student-id="${esc(member.studentId)}"
                                        data-photo="${esc(member.photoImageUrl || "")}"
                                        data-campaign="${esc(member.campaignImageUrl || "")}"
                                        data-background="${esc(member.backgroundImageUrl || "")}"
                                    >

                                        <span class="existing-member-student-id">
                                            ${esc(member.studentId)}
                                        </span>

                                        <span class="existing-member-name">
                                            ${esc(
                                                [
                                                    member.firstName,
                                                    member.middleName,
                                                    member.lastName
                                                ]
                                                .filter(Boolean)
                                                .join(" ")
                                            )}
                                        </span>

                                        <span class="existing-member-position">
                                            ${esc(member.position)}
                                        </span>

                                        <div class="existing-member-files">

                                            <button
                                                type="button"
                                                class="existing-file-btn view-member-photo"
                                                title="View Photo">
                                                <i class="bi bi-image"></i>
                                                Photo
                                            </button>

                                            <button
                                                type="button"
                                                class="existing-file-btn view-member-background"
                                                title="View Background">
                                                <i class="bi bi-file-earmark-text"></i>
                                                Background
                                            </button>

                                        </div>

                                    </div>
                                `).join("")}

                            </div>

                        </div>

                    </div>

                </div>

            </div>

            <section class="edit-partylists-panel">

                <form class="partylists-form edit-partylists-form" novalidate>

                    <div class="form-section">

                        <div class="form-group">

                            <label>Partylist Name</label>

                            <input
                                type="text"
                                value="${esc(partylist.name)}"
                                class="edit-partylists-name-input"
                            >

                            <small class="inline-error edit-partylists-name-error"></small>

                        </div>

                        <div class="form-group">

                            <label>Description</label>

                            <input
                                type="text"
                                value="${esc(partylist.description || "")}"
                            >

                        </div>

                        <div class="form-group">

                            <label>School Year</label>

                            <input
                                type="text"
                                value="${esc(partylist.schoolYear)}"
                                class="edit-partylists-schoolyear-input"
                            >

                            <small class="inline-error edit-partylists-schoolyear-error"></small>

                        </div>

                        <div class="form-group">

                            <label>Campus</label>

                            <select
                                class="edit-partylists-campus"
                                data-campus-id="${esc(partylist.campus?.id || "")}"
                                required
                            >
                                <option value="">Loading campuses...</option>
                            </select>

                            <small class="inline-error edit-partylists-campus-error"></small>

                        </div>

                    </div>

                    <div class="form-section members-section">

                        <div class="partylists-divider"></div>

                        <h3>Members</h3>

                        <div class="members-list edit-members-list"></div>

                        <button
                            type="button"
                            class="add-member-btn edit-add-member">
                            <i class="bi bi-plus-lg"></i>
                            Add More Member
                        </button>

                    </div>

                    <div class="form-buttons">

                        <button
                            type="button"
                            class="discard-btn cancel-partylists-edit">
                            Cancel
                        </button>

                        <button
                            type="submit"
                            class="save-btn">
                            <i class="bi bi-check-circle"></i>
                            Save Changes
                        </button>

                    </div>

                </form>

            </section>

        </section>
    `;

    return card;
}

function createEditPartylistPanel(partylist) {

    const campusId =
        partylist.campus?.id || "";

    const members =
        partylist.members || [];

    return `
        <section class="edit-partylists-panel">

            <form
                class="partylists-form edit-partylists-form"
                novalidate
            >

                <div class="form-section">

                    <div class="form-group">

                        <label>Partylist Name</label>

                        <input
                            type="text"
                            value="${esc(partylist.name || "")}"
                            class="edit-partylists-name-input"
                        >

                        <small
                            class="inline-error edit-partylists-name-error"
                        ></small>

                    </div>

                    <div class="form-group">

                        <label>Description</label>

                        <input
                            type="text"
                            value="${esc(partylist.description || "")}"
                            class="edit-partylists-description-input"
                        >

                    </div>

                    <div class="form-group">

                        <label>School Year</label>

                        <input
                            type="text"
                            value="${esc(partylist.schoolYear || "")}"
                            class="edit-partylists-schoolyear-input"
                        >

                        <small
                            class="inline-error edit-partylists-schoolyear-error"
                        ></small>

                    </div>

                    <div class="form-group">

                        <label>Campus</label>

                        <select
                            class="edit-partylists-campus"
                            data-campus-id="${esc(campusId)}"
                            required
                        >
                            <option value="">Select Campus</option>
                        </select>

                        <small
                            class="inline-error edit-partylists-campus-error"
                        ></small>

                    </div>

                    <div class="form-group">

                        <label>Partylist Poster</label>

                        <div class="partylists-file-upload">

                            <input
                                type="file"
                                class="partylists-poster-input edit-poster-input"
                                accept="image/*,.pdf,application/pdf"
                                hidden
                            >

                            <button
                                type="button"
                                class="partylists-file-btn"
                            >
                                <i class="bi bi-file-earmark-image"></i>
                                <span>Change Poster</span>
                            </button>

                            <div class="partylists-file-preview"></div>

                        </div>

                    </div>

                    <div class="form-group">

                        <label>
                            Partylist Logo
                            <span class="optional-label">
                                (Optional)
                            </span>
                        </label>

                        <div class="partylists-file-upload">

                            <input
                                type="file"
                                class="partylists-logo-input edit-logo-input"
                                accept="image/*"
                                hidden
                            >

                            <button
                                type="button"
                                class="partylists-file-btn"
                            >
                                <i class="bi bi-image"></i>
                                <span>Change Logo</span>
                            </button>

                            <div class="partylists-file-preview"></div>

                        </div>

                    </div>

                </div>

                <div class="form-section members-section">

                    <div class="partylists-divider"></div>

                    <h3>Members</h3>

                    <div class="members-list edit-members-list"></div>

                    <button
                        type="button"
                        class="add-member-btn edit-add-member"
                    >
                        <i class="bi bi-plus-lg"></i>
                        Add More Member
                    </button>

                </div>

                <div class="form-buttons">

                    <button
                        type="button"
                        class="discard-btn cancel-partylists-edit"
                    >
                        Cancel
                    </button>

                    <button
                        type="submit"
                        class="save-btn"
                    >
                        <i class="bi bi-check-circle"></i>
                        Save Changes
                    </button>

                </div>

            </form>

        </section>
    `;
}






async function saveEditedPartylists(card) {
    if (!card) throw new Error("Partylist card is missing.");

    const form = card.querySelector(".edit-partylists-form");
    if (!form) throw new Error("Edit form is missing.");

    const partylistId = card.dataset.id;

    if (!partylistId) {
        throw new Error("Partylist ID is missing.");
    }

    const nameInput =
        form.querySelector(".edit-partylists-name-input");

    const descriptionInput =
        form.querySelector(".edit-partylists-description-input") ||
        form.querySelector(
            'input:not(.edit-partylists-name-input):not(.edit-partylists-schoolyear-input)'
        );

    const schoolYearInput =
        form.querySelector(".edit-partylists-schoolyear-input");

    const campusSelect =
        form.querySelector(".edit-partylists-campus");

    const memberList =
        form.querySelector(".edit-members-list");

    const campusId = campusSelect?.value || "";

    if (!campusId) {
        throw new Error("Please select a campus.");
    }

    console.log("FINAL CAMPUS ID:", campusId);

    const members = memberList
        ? [...memberList.querySelectorAll(".member-row")]
            .map(collectMemberData)
            .map(member => ({
                studentId: member.studentId,
                firstName: member.firstName,
                middleName: member.middleName || null,
                lastName: member.lastName,
                position: member.position,
                campaignImageUrl: member.campaign || null,
                backgroundImageUrl: member.background || null,
                photoImageUrl: member.photo || null
            }))
        : [];

    const payload = {
        name: nameInput?.value.trim() || "",
        description: descriptionInput?.value.trim() || "",
        schoolYear: schoolYearInput?.value.trim() || "",
        campus: {
            id: campusId
        },
        members
    };

    const response = await fetch(
        `/superadmin/api/partylists/${partylistId}`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        }
    );

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to update partylist.");
    }

    const updated = await response.json();

    const title =
        card.querySelector(".partylists-item-title h3");

    const description =
        card.querySelector(".partylists-description");

    const campusDisplay =
        card.querySelector(".partylists-campus span");

    const detailCampus =
        card.querySelector(".existing-partylists-campus");

    if (title) {
        title.textContent = updated.name || payload.name;
    }

    if (description) {
        description.textContent =
            updated.description ||
            payload.description ||
            "";
    }

    const returnedCampusName =
        updated.campus?.name ||
        campusSelect?.selectedOptions?.[0]?.textContent.trim() ||
        "";

    if (campusDisplay) {
        campusDisplay.textContent = returnedCampusName;
    }

    if (detailCampus) {
        detailCampus.textContent = returnedCampusName;
    }

    card.dataset.id =
        updated.id || partylistId;

    card.dataset.campusId =
        updated.campus?.id || campusId;

    card.dataset.campus =
        returnedCampusName;

    card.classList.remove("editing", "show");

    const icon =
        card.querySelector(".expand-partylists i");

    if (icon) {
        icon.className = "bi bi-chevron-down";
    }

    refreshExisting();

    showSuccessToast(
        "Updated",
        `${updated.name || payload.name} was updated successfully.`
    );

    return updated;
}


async function saveNewPartylists(form) {
    if (!form) return;

    const name = document.getElementById("partylistsName")?.value.trim() || "";
    const campusId =
        document.getElementById("partylistsCampus")?.value || "";

    if (!campusId) {
        showSuccessToast(
            "Save Failed",
            "Please select a campus."
        );
        return;
    }
    const schoolYear = document.getElementById("partylistsSchoolYear")?.value.trim() || "";
    const description = document.getElementById("partylistsDescription")?.value.trim() || "";

    const memberList = document.getElementById("createMembersList");

    const members = memberList
        ? [...memberList.querySelectorAll(".member-row")].map(collectMemberData)
        : [];

    const payload = {
        name,
        description,
        schoolYear,

        campus: {
            id: campusId
        },

        status: "ACTIVE",

        members: members.map(member => ({
            studentId: member.studentId,
            lastName: member.lastName,
            firstName: member.firstName,
            middleName: member.middleName || null,
            position: member.position,

            campaignImageUrl:
                member.campaign || null,

            backgroundImageUrl:
                member.background || null,

            photoImageUrl:
                member.photo || null
        }))
    };

    try {
        showActionLoading(
            "Saving Partylist",
            "Please wait while the partylist and members are being saved."
        );

        const response = await fetch(PARTYLIST_API, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errorText = await response.text();

            console.error(
                "Partylist save failed:",
                response.status,
                errorText
            );

            throw new Error(errorText || "Failed to save partylist.");
        }

        hideActionLoading();

        resetCreateForm();
        clearAllInlineErrors();

        showSuccessToast(
            "Added",
            `${name} was added successfully.`
        );

        await loadPartylists();

    } catch (error) {
        console.error("Partylist creation failed:", error);

        hideActionLoading();

        showSuccessToast(
            "Save Failed",
            "Unable to save the partylist."
        );
    }
}


function resetCreateForm() {
    const form = document.getElementById("createPartylistsForm");
    if (!form) return;

    form.reset();

    // form.reset() doesn't clear our custom preview markup - reset it manually
    document.querySelectorAll("#createPartylists .partylists-file-upload").forEach(upload => {
        const preview = upload.querySelector(".partylists-file-preview");
        const button = upload.querySelector(".partylists-file-btn");
        const isLogo = upload.querySelector(".partylists-logo-input") !== null;

        if (preview) {
            preview.innerHTML = "";
            preview.classList.remove("show");
        }

        if (button) {
            button.classList.remove("has-file");
            button.innerHTML = isLogo
                ? `<i class="bi bi-image"></i><span>Upload Logo</span>`
                : `<i class="bi bi-file-earmark-image"></i><span>Upload Poster</span>`;
        }
    });

    const list = document.getElementById("createMembersList");
    if (!list) return;

    list.innerHTML = "";
    DEFAULT_POSITIONS.forEach(position => addMemberRow(list, { position }, false));
}

// Refresh Existing
function refreshExisting() {
    // Re-apply campus filter after card changes
    const filter = document.getElementById("campusFilter");

    if (!filter) return;

    const selectedCampus = filter.value;

    document.querySelectorAll("#existingPartylists .partylists-item").forEach(card => {
        const cardCampus = card.dataset.campus || "";

        card.style.display =
            selectedCampus === "all" || cardCampus === selectedCampus
                ? ""
                : "none";
    });
}




// Close file preview when clicking outside
document.addEventListener("click", e => {
    if (
        e.target.closest(".member-file-preview") ||
        e.target.closest(".photo-btn") ||
        e.target.closest(".campaign-btn") ||
        e.target.closest(".background-btn") ||
        e.target.closest(".existing-member-preview") ||
        e.target.closest(".view-member-photo") ||
        e.target.closest(".view-member-background")
    ) {
        return;
    }

    document
        .querySelectorAll(".member-file-preview.show")
        .forEach(preview => {
            preview.classList.remove("show");
        });
});



// Partylists Tabs
function initializePartylistsTabs() {
    const tabs = document.querySelectorAll(".partylists-tab");
    const sections = document.querySelectorAll(".partylists-section");

    tabs.forEach(tab => {
        tab.addEventListener("click", () => {
            const section = tab.dataset.section;

            tabs.forEach(item => item.classList.remove("active"));
            sections.forEach(item => item.classList.remove("active"));
            tab.classList.add("active");

            if (section === "create") {
                document.getElementById("createPartylists")?.classList.add("active");
            }

            if (section === "existing") {
                document.getElementById("existingPartylists")?.classList.add("active");
            }
        });
    });
}

function initializeCampusFilter() {
    const filter = document.getElementById("campusFilter");
    if (!filter) return;

    filter.addEventListener("change", () => {
        const selectedCampus = filter.value;
        const cards = document.querySelectorAll("#existingPartylists .partylists-item");

        cards.forEach(card => {
            const cardCampus = card.dataset.campus || "";
            card.style.display = (selectedCampus === "all" || cardCampus === selectedCampus) ? "" : "none";
        });
    });
}


async function restorePartylist(id) {
    try {
        showActionLoading(
            "Restoring Partylist",
            "Please wait while the partylist is being restored."
        );

        const response = await fetch(
            `${PARTYLIST_API}/${id}/restore`,
            {
                method: "PUT"
            }
        );

        if (!response.ok) {
            throw new Error("Failed to restore partylist.");
        }

        showSuccessToast(
            "Restored",
            "Partylist restored successfully."
        );

        await loadPartylists();

    } catch (error) {
        console.error(error);

        showSuccessToast(
            "Restore Failed",
            "Unable to restore the partylist."
        );

    } finally {
        hideActionLoading();
    }
}

async function loadArchivedPartylists() {
    const response = await fetch(`${PARTYLIST_API}/archived`);

    if (!response.ok) {
        throw new Error("Failed to load archived partylists.");
    }

    return await response.json();
}

async function loadTrashPartylists() {
    const response = await fetch(`${PARTYLIST_API}/trash`);

    if (!response.ok) {
        throw new Error("Failed to load deleted partylists.");
    }

    return await response.json();
}

