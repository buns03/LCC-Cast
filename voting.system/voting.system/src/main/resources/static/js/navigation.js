/* =========================================================
   LCCAST
   SHARED MOBILE NAVIGATION
========================================================= */

document.addEventListener("DOMContentLoaded", function () {

    initializeMobileNavigation();

    applyGlobalBranding();

});

/* =========================================================
   GLOBAL BRANDING (SYSTEM NAME + LOGO)
========================================================= */

async function applyGlobalBranding() {

    try {

        const res = await fetch("/api/public/branding");

        if (!res.ok) {
            return;
        }

        const branding = await res.json();

        const nameEl = document.getElementById("sidebarSystemName");

        const logoImg = document.getElementById("sidebarLogoImg");

        if (nameEl && branding.systemName) {
            nameEl.textContent = branding.systemName;
        }

        if (logoImg && branding.hasLogo) {
            logoImg.src = `/api/public/branding/logo?t=${Date.now()}`;
        }

    } catch (err) {

        console.error("Failed to load branding:", err);

    }

}


/* =========================================================
   INITIALIZE MOBILE NAVIGATION
========================================================= */

function initializeMobileNavigation() {

    const toggleButton =
        document.getElementById("mobileNavToggle");

    const sidebar =
        document.getElementById("sidebar");

    const overlay =
        document.getElementById("mobileNavOverlay");


    /* =====================================================
       CHECK REQUIRED ELEMENTS
    ===================================================== */

    if (!toggleButton) {

        console.warn(
            "LCCast Navigation: #mobileNavToggle was not found."
        );

        return;

    }

    if (!sidebar) {

        console.warn(
            "LCCast Navigation: #sidebar was not found."
        );

        return;

    }

    if (!overlay) {

        console.warn(
            "LCCast Navigation: #mobileNavOverlay was not found."
        );

        return;

    }


    /* =====================================================
       OPEN NAVIGATION
    ===================================================== */

    function openNavigation() {

        sidebar.classList.add("mobile-open");

        overlay.classList.add("mobile-open");

        toggleButton.setAttribute(
            "aria-expanded",
            "true"
        );

        toggleButton.setAttribute(
            "aria-label",
            "Close navigation"
        );

        toggleButton.innerHTML =
            '<i class="bi bi-x-lg"></i>';

        document.body.classList.add("mobile-nav-active");

    }


    /* =====================================================
       CLOSE NAVIGATION
    ===================================================== */

    function closeNavigation() {

        sidebar.classList.remove("mobile-open");

        overlay.classList.remove("mobile-open");

        toggleButton.setAttribute(
            "aria-expanded",
            "false"
        );

        toggleButton.setAttribute(
            "aria-label",
            "Open navigation"
        );

        toggleButton.innerHTML =
            '<i class="bi bi-list"></i>';

        document.body.classList.remove("mobile-nav-active");

    }


    /* =====================================================
       TOGGLE BUTTON
    ===================================================== */

    toggleButton.addEventListener("click", function (event) {

        event.preventDefault();

        event.stopPropagation();

        if (
            sidebar.classList.contains("mobile-open")
        ) {

            closeNavigation();

        } else {

            openNavigation();

        }

    });


    /* =====================================================
       OVERLAY CLICK
    ===================================================== */

    overlay.addEventListener("click", function () {

        closeNavigation();

    });


    /* =====================================================
       NAVIGATION LINK CLICK
    ===================================================== */

    sidebar.querySelectorAll("a").forEach(function (link) {

        link.addEventListener("click", function () {

            closeNavigation();

        });

    });


    /* =====================================================
       ESC KEY
    ===================================================== */

    document.addEventListener("keydown", function (event) {

        if (event.key === "Escape") {

            closeNavigation();

        }

    });


    /* =====================================================
       WINDOW RESIZE
    ===================================================== */

    window.addEventListener("resize", function () {

        if (window.innerWidth > 768) {

            closeNavigation();

        }

    });

}