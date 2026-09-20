/* =========================================================
   LCCAST - LIVE RESULTS
   =========================================================

   Handles:

   - SSC / Department tab switching
   - Live SSC campus results
   - Live department results
   - Department filtering
   - Candidate rendering
   - Fullscreen campus/department viewing
   - Live polling
   - Backend-ready data structure

   Replace loadLiveResultsData() with the real backend API later.

========================================================= */


document.addEventListener("DOMContentLoaded", () => {

    initializeLiveResults();

});


/* =========================================================
   GLOBAL STATE
========================================================= */

let liveResultsData = null;

/* =========================================================
   CREATE ANONYMOUS CANDIDATE DATA
========================================================= */

const DEFAULT_CANDIDATE_IMAGE =
    "data:image/svg+xml;charset=UTF-8," +
    encodeURIComponent(`
        <svg
            xmlns="http://www.w3.org/2000/svg"
            width="160"
            height="160"
            viewBox="0 0 160 160"
        >
            <rect
                width="160"
                height="160"
                rx="24"
                fill="#EEF0F5"
            />
            <circle
                cx="80"
                cy="57"
                r="27"
                fill="#9CA3AF"
            />
            <path
                d="M35 135c4-29 21-45 45-45s41 16 45 45"
                fill="#9CA3AF"
            />
        </svg>
    `);

function resolveCandidateImage(photoPath) {
    if (!photoPath) return DEFAULT_CANDIDATE_IMAGE;
    if (
        photoPath.startsWith("http://") ||
        photoPath.startsWith("https://") ||
        photoPath.startsWith("data:") ||
        photoPath.startsWith("/files/")
    ) {
        return photoPath;
    }
    return `/files/${photoPath}`;
}

let previousVotes = {};

/* =========================================================
   CURRENT FILTER STATE
========================================================= */

let selectedDepartmentCampus = "ALL";

let selectedDepartment = "ALL";

/* =========================================================
   ANONYMOUS CANDIDATE ORDER
========================================================= */

const anonymousCandidateOrder = new Map();


/* =========================================================
   POSITIONS
========================================================= */

const POSITIONS = [

    {
        key: "President",
        label: "President"
    },

    {
        key: "VP",
        label: "VP"
    },

    {
        key: "Secretary",
        label: "Secretary"
    },

    {
        key: "Treasurer",
        label: "Treasurer"
    },

    {
        key: "Auditor",
        label: "Auditor"
    },

    {
        key: "PRO Internal",
        label: "PRO Internal"
    },

    {
        key: "PRO External",
        label: "PRO External"
    }

];

function getPositionList(positions) {
    const keys = Object.keys(positions || {});
    return keys.map(key => {
        const standard = POSITIONS.find(p => p.key === key);
        return standard || { key, label: key };
    });
}

//
///* =========================================================
//   CAMPUSES
//========================================================= */
//
//const CAMPUSES = {
//
//    college: {
//        key: "college",
//        name: "College"
//    },
//
//    muzon: {
//        key: "muzon",
//        name: "Muzon"
//    },
//
//};

/* =========================================================
   ELECTION STATUS
========================================================= */

function getElectionStatus(entity) {
    return String(entity?.phase || "UPCOMING").toLowerCase();
}


/* =========================================================
   SKELETON LOADING
========================================================= */

function buildSkeletonPositionColumn() {
    return `
        <div class="live-skeleton-column">
            <div class="skeleton live-skeleton-heading"></div>
            ${[0, 1].map(() => `
                <div class="live-skeleton-candidate">
                    <div class="skeleton live-skeleton-avatar"></div>
                    <div class="live-skeleton-candidate-info">
                        <div class="skeleton live-skeleton-line" style="width:70%"></div>
                        <div class="skeleton live-skeleton-line" style="width:45%"></div>
                        <div class="skeleton live-skeleton-bar"></div>
                    </div>
                </div>
            `).join("")}
        </div>
    `;
}

function buildSkeletonEntityCard() {
    return `
        <article class="result-entity-container live-skeleton-card">
            <div class="result-entity-header">
                <div class="result-entity-identity">
                    <div class="skeleton live-skeleton-line" style="width:80px;height:10px;"></div>
                    <div class="skeleton live-skeleton-line" style="width:220px;height:24px;margin-top:8px;"></div>
                    <div class="skeleton live-skeleton-line" style="width:160px;height:14px;margin-top:8px;"></div>
                </div>
                <div class="result-entity-actions">
                    <div class="skeleton live-skeleton-line" style="width:70px;height:34px;"></div>
                </div>
            </div>
            <div class="result-entity-section voting-results-section">
                <div class="positions-grid">
                    ${[0, 1, 2, 3].map(buildSkeletonPositionColumn).join("")}
                </div>
            </div>
        </article>
    `;
}

function showLiveResultsSkeleton() {
    const deptContainer = document.getElementById("departmentLiveContainer");
    const skeletonHTML = [0, 1].map(buildSkeletonEntityCard).join("");
    if (deptContainer) deptContainer.innerHTML = skeletonHTML;
}

/* =========================================================
   INITIALIZE
========================================================= */

async function initializeLiveResults() {
    showLiveResultsSkeleton();
    liveResultsData = await loadLiveResultsData();
    initializeFullscreenButtons();
    renderLiveResults();
    connectLiveResultsSocket();
}


/* =========================================================
   BACKEND DATA LOADER
=========================================================

   Later replace with:

   async function loadLiveResultsData() {

       const response =
           await fetch(
               "/api/superadmin/live-results"
           );

       if (!response.ok) {
           throw new Error(
               "Failed to load live results"
           );
       }

       return await response.json();

   }

========================================================= */

  async function loadLiveResultsData() {
      const response = await fetch("/admin-dept/live-results/data");
      if (!response.ok) throw new Error("Failed to load live results");
      return await response.json();
  }

let stompClient = null;

function connectLiveResultsSocket() {
    const campusId = document.getElementById("wsCampusId")?.value;
    const departmentId = document.getElementById("wsDepartmentId")?.value;

    if (!campusId || !departmentId) {
        console.error("Missing campusId/departmentId — cannot subscribe to live results");
        return;
    }

    const socket = new SockJS("/ws-analytics");
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    stompClient.connect({}, () => {
        stompClient.subscribe(
            `/topic/live-results/department/${campusId}/${departmentId}`,
            message => {
                const entity = JSON.parse(message.body);
                liveResultsData = { departments: [entity] };
                updateLiveVotesInPlace(liveResultsData);
                updateLastUpdated();
            }
        );
    });
}

/* =========================================================
   UPDATE LIVE VOTES IN PLACE (no re-render, no scroll jump)
========================================================= */

function updateLiveVotesInPlace(newData) {

    updateEntityGroupInPlace(newData?.departments || [], "department");

}

function updateEntityGroupInPlace(entities, prefix) {
    entities.forEach(entity => {
        const cardId = `${prefix}-${entity.id}`;
        const card = document.getElementById(cardId);

        if (!card) {
            if (document.fullscreenElement) return;
            renderLiveResults();
            return;
        }

        if (entity.votingType === "REPRESENTATIVE" || entity.votingType === "PARTYLIST") {
            updateDepartmentCardInPlace(card, entity);
            return;
        }

        const existingStatusEl = card.querySelector(".election-status");
        const previousPhase = existingStatusEl
            ? [...existingStatusEl.classList].find(c => c.startsWith("status-"))?.replace("status-", "")
            : null;

        const newPhase = getElectionStatus(entity);

        if (previousPhase && previousPhase !== newPhase) {
            if (document.fullscreenElement === card) {
                const newCard = createDepartmentCard(entity.name || entity.id, entity);
                while (card.firstChild) card.removeChild(card.firstChild);
                while (newCard.firstChild) card.appendChild(newCard.firstChild);
                return;
            }
            renderLiveResults();
            return;
        }

        // Patch "Votes Cast" total for this entity
        const votesCastEl = document.getElementById(`${cardId}-votes-cast`);
        if (votesCastEl) {
            votesCastEl.textContent = Number(entity.votesCast || 0);
        }

        // Patch each candidate row's votes + bar, per position
        Object.entries(entity.positions || {}).forEach(([positionKey, candidates]) => {

            const positionId = `${entity.id}-${positionKey}`;
            const isAnonymous = false;

            const displayCandidates = getDisplayCandidates(
                candidates,
                isAnonymous,
                `${entity.id}-${positionKey}`
            );

            const totalPositionVotes = displayCandidates.reduce(
                (total, c) => total + Number(c.votes || 0),
                0
            );

            displayCandidates.forEach((candidate, index) => {

                const displayCandidate = isAnonymous
                    ? createAnonymousCandidate(candidate, index)
                    : candidate;

                const row = card.querySelector(
                    `.live-candidate[data-candidate-id="${displayCandidate.id}"][data-position-id="${positionId}"]`
                );

                if (!row) return; // candidate list shape changed; skip patch for this one

                const votes = Number(displayCandidate.votes || 0);
                const percentage = totalPositionVotes > 0
                    ? (votes / totalPositionVotes) * 100
                    : 0;

                const voteCountEl = row.querySelector('[data-role="vote-count"]');
                const progressBarEl = row.querySelector('[data-role="progress-bar"]');
                const percentEl = row.querySelector('[data-role="percent"]');

                if (voteCountEl) {
                    const newText = `${votes} vote${votes === 1 ? "" : "s"}`;
                    if (voteCountEl.textContent !== newText) {
                        voteCountEl.textContent = newText;
                        voteCountEl.classList.add("vote-updated");
                    }
                }

                if (progressBarEl) {
                    progressBarEl.style.width = `${percentage}%`;
                }

                if (percentEl) {
                    percentEl.textContent = `${percentage.toFixed(1)}%`;
                }

            });

        });

    });

}

function updateDepartmentCardInPlace(card, entity) {

    const newCard =
        createDepartmentCard(
            entity.name || entity.id,
            entity
        );

    /*
     * Move the generated child nodes instead of using
     * innerHTML. This preserves the click listeners
     * attached by createDepartmentCard().
     */
    while (card.firstChild) {
        card.removeChild(card.firstChild);
    }

    while (newCard.firstChild) {
        card.appendChild(newCard.firstChild);
    }
}


/* =========================================================
   FULLSCREEN BUTTONS
========================================================= */

function initializeFullscreenButtons() {

    /*
       Event delegation.

       This works even for fullscreen
       buttons created dynamically after
       the page has loaded.
    */

    document.addEventListener(
        "click",
        async event => {

            const button =
                event.target.closest(
                    "[data-fullscreen]"
                );


            if (!button) return;


            const targetId =
                button.dataset.fullscreen;


            const target =
                document.getElementById(
                    targetId
                );


            if (!target) {

                console.error(
                    "Fullscreen target not found:",
                    targetId
                );

                return;

            }


            await enterFullscreen(
                target
            );

        }
    );


    document.addEventListener(
        "fullscreenchange",
        () => {

            updateFullscreenButtons();


        }
    );

}


/* =========================================================
   ENTER FULLSCREEN
========================================================= */

async function enterFullscreen(element) {

    try {

        if (
            document.fullscreenElement ===
            element
        ) {

            await document.exitFullscreen();

            return;

        }


        if (
            document.fullscreenElement
        ) {

            await document.exitFullscreen();

        }


        await element.requestFullscreen();

    } catch (error) {

        console.error(
            "Fullscreen error:",
            error
        );

    }

}


/* =========================================================
   UPDATE FULLSCREEN BUTTON TEXT
========================================================= */

function updateFullscreenButtons() {

    const fullscreenElement =
        document.fullscreenElement;


    document
        .querySelectorAll(
            "[data-fullscreen]"
        )
        .forEach(button => {

            const target =
                document.getElementById(
                    button.dataset.fullscreen
                );


            const icon =
                button.querySelector("i");


            const text =
                button.querySelector("span");


            if (
                fullscreenElement ===
                target
            ) {

                if (icon) {

                    icon.className =
                        "bi bi-fullscreen-exit";

                }


                if (text) {

                    text.textContent =
                        "Exit Fullscreen";

                }

            } else {

                if (icon) {

                    icon.className =
                        "bi bi-fullscreen";

                }


                if (text) {

                    text.textContent =
                        "Fullscreen";

                }

            }

        });

}


/* =========================================================
   RENDER EVERYTHING
========================================================= */

function renderLiveResults() {
    const departmentSection = document.getElementById("departmentLiveSection");
    departmentSection.style.display = "";
    renderDepartments(
        document.getElementById("liveDepartmentFilter")?.value || "ALL",
        document.getElementById("departmentCampusFilter")?.value || "ALL"
    );
    updateLastUpdated();
}

/* =========================================================
   RENDER WINNERS
========================================================= */

function renderWinnerList(
    container,
    positions
) {

    if (!container) return;


    container.innerHTML = "";


    getPositionList(positions).forEach(position => {

        const candidates =
            positions?.[position.key] || [];


        if (!candidates.length) {
            return;
        }


        /*
           Winner cards only exist after
           the election has ended.

           Therefore always use the
           original candidate information.
        */

        const winner =
            [...candidates]
                .sort(
                    (a, b) =>
                        Number(b.votes || 0) -
                        Number(a.votes || 0)
                )[0];


        const item =
            document.createElement("div");


        item.className =
            "winner-item";


        item.innerHTML = `

            <img
                src="${resolveCandidateImage(winner.photo)}"
                alt="${escapeHTML(winner.name)}"
            >

            <div class="winner-item-info">

                <span>
                    ${escapeHTML(position.label)}
                </span>

                <strong>
                    ${escapeHTML(winner.name)}
                </strong>

            </div>


            <div class="winner-item-votes">

                <strong>
                    ${Number(winner.votes || 0)}
                </strong>

                <span>
                    Votes
                </span>

            </div>

        `;


        const image =
            item.querySelector("img");


        image.onerror = () => {

            image.src =
                DEFAULT_CANDIDATE_IMAGE

        };


        container.appendChild(item);

    });

}

function renderPositionWinnerCards(
    container,
    positions,
    exportPrefix
) {

    if (!container) return;

    container.innerHTML = "";

    getPositionList(positions).forEach(position => {

        const candidates =
            positions?.[position.key] || [];

        if (!candidates.length) {
            return;
        }

        /*
           Highest voter is always first.
        */
        const orderedCandidates =
            [...candidates].sort(
                (a, b) =>
                    Number(b.votes || 0) -
                    Number(a.votes || 0)
            );

        const totalVotes =
            orderedCandidates.reduce(
                (total, candidate) =>
                    total +
                    Number(candidate.votes || 0),
                0
            );

        const positionCard =
            document.createElement("article");

        positionCard.className =
            "position-winner-card";

        positionCard.innerHTML = `

            <div class="position-winner-card-header">

                <div>
                    <span>
                        Position
                    </span>

                    <h3>
                        ${escapeHTML(position.label)}
                    </h3>
                </div>

                <button
                    type="button"
                    class="download-position-btn"
                    title="Download ${escapeHTML(position.label)} PNG"
                >
                    <i class="bi bi-download"></i>
                    PNG
                </button>

            </div>

            <div class="position-winner-card-content">

                <div class="position-winner-list"></div>

            </div>

        `;

        const list =
            positionCard.querySelector(
                ".position-winner-list"
            );

        orderedCandidates.forEach(
            candidate => {

                const votes =
                    Number(candidate.votes || 0);

                const percentage =
                    totalVotes > 0
                        ? (votes / totalVotes) * 100
                        : 0;

                const item =
                    document.createElement("div");

                item.className =
                    "position-winner-candidate";

                item.innerHTML = `

                    <img
                        src="${resolveCandidateImage(candidate.photo)}"
                        alt="${escapeHTML(candidate.name)}"
                    >

                    <div class="position-winner-info">

                        <strong>
                            ${escapeHTML(
                        candidate.name ||
                        "Unknown Candidate"
                    )}
                        </strong>

                        <span>
                            ${escapeHTML(
                        candidate.partylist ||
                        "Independent"
                    )}
                        </span>

                        <div class="position-winner-progress">

                            <div
                                class="position-winner-progress-track"
                            >
                                <div
                                    class="position-winner-progress-bar"
                                    style="width:${percentage}%"
                                ></div>
                            </div>

                            <strong>
                                ${percentage.toFixed(1)}%
                            </strong>

                        </div>

                        <small>
                            ${votes}
                            vote${votes === 1 ? "" : "s"}
                        </small>

                    </div>

                `;

                const image =
                    item.querySelector("img");

                image.onerror = () => {
                    image.src =
                        DEFAULT_CANDIDATE_IMAGE
                };

                list.appendChild(item);

            }
        );

        const downloadButton =
            positionCard.querySelector(
                ".download-position-btn"
            );

        downloadButton?.addEventListener(
            "click",
            () => {

                downloadPositionWinnerCard(
                    positionCard,
                    position.label,
                    exportPrefix,
                    orderedCandidates
                );

            }
        );

        container.appendChild(
            positionCard
        );

    });

}
/* =========================================================
   RENDER POSITIONS GRID
========================================================= */

function renderPositionsGrid(
    container,
    positions,
    campusKey,
    isAnonymous = false
) {

    if (!container) return;


    container.innerHTML = "";


    const positionKeys = Object.keys(positions || {});
        const isStandardPositions = positionKeys.every(
            k => POSITIONS.some(p => p.key === k)
        );
        const positionList = positionKeys.map(key => {
            const standardPosition = POSITIONS.find(
                position => position.key === key
            );

            return standardPosition || {
                key,
                label: key
            };
        });

        positionList.forEach(position => {

        const originalCandidates =
            positions?.[position.key] || [];


        /*
           Randomize candidates only when
           the election is currently running.
        */

        const orderedCandidates =
            getDisplayCandidates(
                originalCandidates,
                isAnonymous,
                `${campusKey}-${position.key}`
            );


        /*
           Create anonymous display identities.
        */

        const displayCandidates =
            isAnonymous
                ? orderedCandidates.map(
                    (candidate, index) =>
                        createAnonymousCandidate(
                            candidate,
                            index
                        )
                )
                : orderedCandidates;


        container.appendChild(
            createPositionColumn(
                position,
                displayCandidates,
                `${campusKey}-${position.key}`,
                isAnonymous
            )
        );

    });

}


/* =========================================================
   DEPARTMENT
========================================================= */

function renderDepartments(
    departmentFilter = selectedDepartment,
    campusFilter = selectedDepartmentCampus
) {

    const container =
        document.getElementById(
            "departmentLiveContainer"
        );


    if (!container) return;


    const departments =
        liveResultsData?.departments || [];


    selectedDepartment =
        departmentFilter || "ALL";


    selectedDepartmentCampus =
        campusFilter || "ALL";


    const filteredDepartments =
        departments.filter(department => {

            if (
                selectedDepartment !== "ALL" &&
                department.key !== selectedDepartment
            ) {
                return false;
            }


                        if (
                            selectedDepartmentCampus !== "ALL" &&
                            department.campusId !==
                            selectedDepartmentCampus
                        ) {
                            return false;
                        }


            return true;

        });


    container.innerHTML = "";


    filteredDepartments.forEach(department => {

        container.appendChild(
            createDepartmentCard(
                department.name || department.id,
                department
            )
        );

    });


    if (filteredDepartments.length === 0) {

        container.innerHTML = `

            <div class="department-filter-empty">

                <div class="live-empty-icon">

                    <i class="bi bi-search"></i>

                </div>

                <h3>
                    No Department Results
                </h3>

                <p>
                    No department election matches the
                    selected campus and department.
                </p>

            </div>

        `;

    }


    const totalVotes =
        filteredDepartments.reduce(
            (total, department) =>
                total +
                Number(department.votesCast || 0),
            0
        );


    updateElement(
        "departmentLiveVotes",
        totalVotes
    );

}


/* =========================================================
   CREATE DEPARTMENT CARD
========================================================= */

function createDepartmentCard(
    departmentCode,
    department
) {

    const card =
        document.createElement("article");


    const cardId =
        `department-${department.id}`;


    card.id = cardId;

    card.className =
        "result-entity-container department-entity-container";


   const departmentName = department.name || departmentCode;
   const departmentTitle = department.title || "";
   const campusName = department.campusName || "Campus Not Assigned";


    const electionStatus =
        getElectionStatus(
            department
        );


       const statusLabel = {

           ongoing: "ONGOING",

           upcoming: "UPCOMING",

           concluded: "CONCLUDED"

       }[electionStatus] || "UPCOMING";


    card.innerHTML = `

        <!-- =====================================================
             IDENTITY
        ====================================================== -->

        <div class="result-entity-header">

            <div class="result-entity-identity">

                <span class="result-entity-label">
                    DEPARTMENT
                </span>


                <div class="result-entity-title-row">

                    <h2>
                        ${escapeHTML(departmentName)}
                    </h2>


                    <span
                        class="
                            election-status
                            status-${electionStatus}
                        "
                    >
                        ${statusLabel}
                    </span>

                </div>


                <h3 class="result-entity-subtitle">

                     ${escapeHTML(departmentTitle)}

                </h3>


                <p>

                    <i class="bi bi-building"></i>

                    ${escapeHTML(
        campusName
    )}

                    Campus

                </p>

            </div>


            <div class="result-entity-actions">

                <div class="campus-votes">

                    <span>
                        Votes Cast
                    </span>

                    <strong id="${cardId}-votes-cast">${Number(department.votesCast || 0)}</strong>

                </div>


                ${electionStatus === "ongoing" ? `
                <button
                    type="button"
                    class="fullscreen-btn"
                    data-fullscreen="${cardId}"
                >
                    <i class="bi bi-fullscreen"></i>
                    <span>Fullscreen</span>
                </button>` : ""}

            </div>

        </div>


        <!-- =====================================================
             WINNER CARD
        ====================================================== -->

        <div
            class="result-entity-section winner-section"
            id="${cardId}-winner-section"
        ></div>


        <!-- =====================================================
             POSITION WINNER CARDS
        ====================================================== -->

        <div
            class="result-entity-section position-winner-section"
            id="${cardId}-position-section"
        ></div>


        <!-- =====================================================
             VOTING RESULTS
        ====================================================== -->

        <div class="result-entity-section voting-results-section">

            <div class="result-entity-section-heading">

                <div>

                    <span>

                        <i class="bi bi-bar-chart-fill"></i>

                        VOTING RESULTS

                    </span>


                    <p>
                        Results for every department position
                    </p>

                </div>

            </div>


            <div
                class="positions-grid"
                id="${cardId}-results"
            ></div>

        </div>

    `;


    /* =========================================================
       WINNER CONTENT
    ========================================================= */

        if (electionStatus === "concluded") {

        const winnerContainer =
            card.querySelector(
                `#${cardId}-winner-section`
            );


        const winnerSection =
            document.createElement("section");


        winnerSection.className =
            "winner-card department-winner-card";


        winnerSection.innerHTML = `

            <div class="winner-card-header">

                <div>

                    <span class="result-section-label">

                        <i class="bi bi-trophy-fill"></i>

                        WINNER CARD

                    </span>


                    <h3>
                        ${escapeHTML(
            departmentCode
        )}
                    </h3>


                    <p>

                        ${escapeHTML(
            departmentName
        )}

                    </p>

                </div>


                <button
                    type="button"
                    class="download-winner-btn"
                    data-download-winners="${cardId}-winner"
                >

                    <i class="bi bi-download"></i>

                    Download PNG

                </button>

            </div>


            <div
                class="winner-card-content"
                id="${cardId}-winner"
            >

                <div class="winner-card-title">

                    <span>
                        Department
                    </span>


                    <strong>

                        ${escapeHTML(
            departmentCode
        )}

                        Winners!

                    </strong>

                </div>


                <div class="winner-list"></div>

            </div>

        `;


        winnerContainer.appendChild(
            winnerSection
        );


        renderWinnerList(

            winnerSection.querySelector(
                ".winner-list"
            ),

            department.positions

        );


        /* =====================================================
           POSITION WINNER CARDS
        ====================================================== */

        const positionContainer =
            card.querySelector(
                `#${cardId}-position-section`
            );


        const positionSection =
            document.createElement("section");


        positionSection.className =
            "position-winners-section";


        positionSection.innerHTML = `

            <div class="position-winners-header">

                <div>

                    <span>
                        WINNER POSITION CARDS
                    </span>


                    <h3>
                        ${escapeHTML(
            departmentCode
        )}
                        Position Results
                    </h3>


                    <p>
                        Downloadable result cards for each position
                    </p>

                </div>

            </div>


            <div
                class="position-winners-grid"
            ></div>

        `;


        positionContainer.appendChild(
            positionSection
        );


        renderPositionWinnerCards(

            positionSection.querySelector(
                ".position-winners-grid"
            ),

            department.positions,

            `department-${departmentCode}`

        );


        const downloadButton =
            winnerSection.querySelector(
                "[data-download-winners]"
            );


        downloadButton?.addEventListener(
            "click",
            () => {

                downloadWinnerCard(

                    winnerSection.querySelector(
                        ".winner-card-content"
                    ),

                    departmentName,

                    campusName,

                    `${departmentCode} Department Election`

                );

            }
        );

    } else {

        card.querySelector(
            `#${cardId}-winner-section`
        )?.remove();


        card.querySelector(
            `#${cardId}-position-section`
        )?.remove();

    }


    /* =========================================================
       VOTING RESULTS
    ========================================================= */

    renderPositionsGrid(
        card.querySelector(".positions-grid"),
        department.positions,
        departmentCode,
        false
    );


    const downloadButton =
        card.querySelector(
            "[data-download-winners]"
        );




    downloadButton?.addEventListener(
        "click",
        () => {

            downloadWinnerCard(
                card.querySelector(
                    ".winner-card-content"
                ),
                departmentName,
                campusName,
                `${departmentCode} Department Election`
            );

        }
    );


    return card;

}

/* =========================================================
   GET DISPLAY CANDIDATES
========================================================= */

function getDisplayCandidates(
    candidates,
    isAnonymous,
    anonymousKey
) {

    if (!candidates || candidates.length === 0) {
        return [];
    }

    /*
       ELECTION ENDED
       -----------------------------------------
       Always show highest voter first.
    */
    if (!isAnonymous) {

        return [...candidates].sort(
            (a, b) =>
                Number(b.votes || 0) -
                Number(a.votes || 0)
        );

    }

    /*
       ELECTION RUNNING
       -----------------------------------------
       The candidate with the highest votes
       ALWAYS stays at the top.

       The remaining candidates are randomized.
       This prevents the live polling refresh
       from constantly changing their positions.
    */

    if (anonymousCandidateOrder.has(anonymousKey)) {

        const savedOrder =
            anonymousCandidateOrder.get(
                anonymousKey
            );

        /*
           Find the current leader.
        */

        let currentLeaderIndex = 0;

        for (
            let i = 1;
            i < candidates.length;
            i++
        ) {

            if (
                Number(candidates[i].votes || 0) >
                Number(
                    candidates[
                        currentLeaderIndex
                    ].votes || 0
                )
            ) {

                currentLeaderIndex = i;

            }

        }


        /*
           Keep the current leader first.

           The remaining candidates keep
           their previous randomized order.
        */

        const remainingOrder =
            savedOrder.filter(
                index =>
                    index !== currentLeaderIndex
            );


        const finalOrder = [

            currentLeaderIndex,

            ...remainingOrder

        ];


        anonymousCandidateOrder.set(
            anonymousKey,
            finalOrder
        );


        return finalOrder
            .map(
                index => candidates[index]
            )
            .filter(Boolean);

    }

    const indexes =
        candidates.map((_, index) => index);

    /*
       Find current top voter.
    */
    let topIndex = 0;

    for (let i = 1; i < candidates.length; i++) {

        if (
            Number(candidates[i].votes || 0) >
            Number(candidates[topIndex].votes || 0)
        ) {
            topIndex = i;
        }

    }

    /*
       Remove top voter from shuffle pool.
    */
    const remainingIndexes =
        indexes.filter(index => index !== topIndex);

    /*
       Shuffle everyone except the leader.
    */
    for (
        let i = remainingIndexes.length - 1;
        i > 0;
        i--
    ) {

        const j =
            Math.floor(
                Math.random() * (i + 1)
            );

        [
            remainingIndexes[i],
            remainingIndexes[j]
        ] = [
                remainingIndexes[j],
                remainingIndexes[i]
            ];

    }

    /*
       Leader is ALWAYS first.
    */
    const finalOrder = [
        topIndex,
        ...remainingIndexes
    ];

    anonymousCandidateOrder.set(
        anonymousKey,
        finalOrder
    );

    return finalOrder
        .map(index => candidates[index])
        .filter(Boolean);

}



function createAnonymousCandidate(candidate, anonymousIndex) {
    return {
        id: candidate.id,
        name: `Candidate ${anonymousIndex + 1}`,
        photo: DEFAULT_CANDIDATE_IMAGE,
        partylist: "Anonymous Partylist",
        votes: Number(candidate.votes || 0)
    };
}


/* =========================================================
   CREATE POSITION COLUMN
========================================================= */

function createPositionColumn(
    position,
    candidates,
    positionId,
    isAnonymous = false
) {

    const column =
        document.createElement(
            "div"
        );


    column.className =
        "position-column";

    column.classList.add(
        `position-${position.key
            .toLowerCase()
            .replace(/\s+/g, "-")}`
    );


    column.dataset.position =
        position.key;


    const heading =
        document.createElement(
            "div"
        );


    heading.className =
        "position-heading";


    heading.textContent =
        position.label;


    column.appendChild(
        heading
    );


    if (
        !candidates ||
        candidates.length === 0
    ) {

        const empty =
            document.createElement(
                "div"
            );


        empty.className =
            "live-no-candidate";


        empty.textContent =
            "No candidates";


        column.appendChild(
            empty
        );


        return column;

    }


    /*
       Calculate the total votes
       for THIS position only.
    */

    const totalPositionVotes =
        candidates.reduce(
            (total, candidate) => {

                return total +
                    Number(
                        candidate.votes || 0
                    );

            },
            0
        );


    candidates.forEach(
        (
            candidate,
            index
        ) => {

            const candidateRow =
                createCandidateRow(
                    candidate,
                    positionId,
                    totalPositionVotes
                );


            /*
               First candidate is always
               the current leader.
            */

            if (index === 0) {

                candidateRow.classList.add(
                    "is-leading"
                );

            }


            column.appendChild(
                candidateRow
            );

        }
    );


    return column;

}


/* =========================================================
   CREATE CANDIDATE ROW
========================================================= */

function createCandidateRow(
    candidate,
    positionId,
    totalPositionVotes
) {

    const row =
        document.createElement(
            "div"
        );


    row.className =
        "live-candidate";

            row.dataset.candidateId = candidate.id;
            row.dataset.positionId = positionId;


    /* =====================================================
       IMAGE
    ===================================================== */

    const image =
        document.createElement(
            "img"
        );


    image.className =
        "live-candidate-image";


    image.src = resolveCandidateImage(candidate.photo);


    image.alt =
        candidate.name || "Candidate";


    image.onerror =
        () => {

            image.src =
                DEFAULT_CANDIDATE_IMAGE

        };


    /* =====================================================
       INFORMATION CONTAINER
    ===================================================== */

    const info =
        document.createElement(
            "div"
        );


    info.className =
        "live-candidate-info";


    /* =====================================================
       CANDIDATE NAME
    ===================================================== */

    const name =
        document.createElement(
            "span"
        );


    name.className =
        "live-candidate-name";


    name.textContent =
        candidate.name ||
        "Unknown Candidate";


    /* =====================================================
       PARTYLIST
    ===================================================== */

    const partylist =
        document.createElement(
            "span"
        );


    partylist.className =
        "live-candidate-partylist";


    partylist.textContent =
        candidate.partylist ||
        "Independent";


    info.appendChild(
        name
    );


    info.appendChild(
        partylist
    );


    /* =====================================================
       VOTE CALCULATION
    ===================================================== */

    const votes =
        Number(
            candidate.votes || 0
        );


    const percentage =
        totalPositionVotes > 0
            ? (votes / totalPositionVotes) * 100
            : 0;


    const roundedPercentage =
        percentage.toFixed(1);


    /* =====================================================
       PROGRESS CONTAINER
    ===================================================== */

    const progress =
        document.createElement(
            "div"
        );


    progress.className =
        "live-candidate-progress";


    /* =====================================================
       PROGRESS TRACK
    ===================================================== */

    const progressTrack =
        document.createElement(
            "div"
        );


    progressTrack.className =
        "live-candidate-progress-track";


    /* =====================================================
       PROGRESS BAR
    ===================================================== */

    const progressBar =
        document.createElement(
            "div"
        );


    progressBar.className =
        "live-candidate-progress-bar";


    progressBar.style.width =
        `${percentage}%`;


    /* =====================================================
       PERCENTAGE
    ===================================================== */

    const percent =
        document.createElement(
            "strong"
        );


    percent.className =
        "live-candidate-percent";


    percent.textContent =
        `${roundedPercentage}%`;


    progressTrack.appendChild(
        progressBar
    );


    progress.appendChild(
        progressTrack
    );


    progress.appendChild(
        percent
    );


    /* =====================================================
       VOTE COUNT
    ===================================================== */

    const voteCount =
        document.createElement(
            "span"
        );


    voteCount.className =
        "live-candidate-vote-count";

        voteCount.dataset.role = "vote-count";

            progressBar.className = "live-candidate-progress-bar";

                progressBar.dataset.role = "progress-bar";

                    percent.className = "live-candidate-percent";

                        percent.dataset.role = "percent";


    voteCount.textContent =
        `${votes} vote${votes === 1 ? "" : "s"}`;


    /* =====================================================
       VOTE UPDATE TRACKING
    ===================================================== */

    const voteKey =
        `${positionId}-${candidate.name}`;


    if (
        previousVotes[voteKey] !== undefined &&
        previousVotes[voteKey] !== votes
    ) {

        voteCount.classList.add(
            "vote-updated"
        );

    }


    previousVotes[voteKey] =
        votes;


    /* =====================================================
       BUILD ROW
    ===================================================== */

    row.appendChild(
        image
    );


    row.appendChild(
        info
    );


    row.appendChild(
        progress
    );


    row.appendChild(
        voteCount
    );


    return row;

}


/* =========================================================
   LAST UPDATED
========================================================= */

function updateLastUpdated() {

    const now =
        new Date();


    const time =
        now.toLocaleTimeString(
            [],
            {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit"
            }
        );


    updateElement(
        "lastUpdatedDepartment",
        `Updated ${time}`
    );

}


/* =========================================================
   UPDATE ELEMENT
========================================================= */

function updateElement(
    id,
    value,
    directElement = null
) {

    const element =
        directElement ||
        document.getElementById(
            id
        );


    if (!element) return;


    element.textContent =
        value;

}

/* =========================================================
   DOWNLOAD WINNER CARD AS 4:3 PNG
========================================================= */

/* =========================================================
   DOWNLOAD WINNER CARD AS DESIGNED 4:3 PNG
========================================================= */

async function downloadWinnerCard(
    element,
    title,
    campusName,
    subtitle
) {

    if (
        !element ||
        typeof html2canvas === "undefined"
    ) {
        return;
    }

    try {

        /*
           Find all winner items from the browser card.
        */

        const winnerItems =
            element.querySelectorAll(
                ".winner-item"
            );


        if (!winnerItems.length) {
            return;
        }


        /*
           Create a completely separate
           export-only container.
        */

        const exportCard =
            document.createElement("div");


        exportCard.style.position = "fixed";
        exportCard.style.left = "-10000px";
        exportCard.style.top = "0";

        exportCard.style.width = "1200px";
        exportCard.style.height = "900px";

        exportCard.style.boxSizing = "border-box";

        exportCard.style.background = "#FFFFFF";

        exportCard.style.padding = "55px 60px";

        exportCard.style.fontFamily =
            "Arial, Helvetica, sans-serif";

        exportCard.style.color = "#202334";

        exportCard.style.overflow = "hidden";


        /*
           HEADER
        */

        const header =
            document.createElement("div");


        header.style.textAlign = "center";

        header.style.marginBottom = "35px";


        const smallTitle =
            document.createElement("div");


        smallTitle.textContent =
            "OFFICIAL ELECTION RESULTS";


        smallTitle.style.fontSize = "14px";

        smallTitle.style.fontWeight = "700";

        smallTitle.style.letterSpacing = "2px";

        smallTitle.style.textTransform =
            "uppercase";


        smallTitle.style.marginBottom = "8px";


        const mainTitle =
            document.createElement("div");


        mainTitle.textContent =
            `${title} Winners`;


        mainTitle.style.fontSize = "38px";

        mainTitle.style.fontWeight = "800";

        mainTitle.style.lineHeight = "1.1";


        const subtitleElement =
            document.createElement("div");


        subtitleElement.textContent =
            `${campusName} Campus • ${subtitle}`;


        subtitleElement.style.marginTop = "8px";

        subtitleElement.style.fontSize = "15px";

        subtitleElement.style.color = "#6B7280";


        header.appendChild(
            smallTitle
        );

        header.appendChild(
            mainTitle
        );

        header.appendChild(
            subtitleElement
        );


        exportCard.appendChild(
            header
        );


        /*
           WINNER GRID

           5 winners per row.
        */

        const grid =
            document.createElement("div");


        grid.style.display = "grid";

        grid.style.gridTemplateColumns =
            "repeat(5, 1fr)";

        grid.style.gridTemplateRows =
            "repeat(2, 1fr)";

        grid.style.gap = "18px";


        grid.style.height = "625px";


        winnerItems.forEach(
            item => {

                const image =
                    item.querySelector("img");


                const position =
                    item.querySelector(
                        ".winner-item-info span"
                    );


                const name =
                    item.querySelector(
                        ".winner-item-info strong"
                    );


                /*
                   Partylist may not exist yet.
                */

                const originalCandidate =
                    findCandidateByName(
                        name?.textContent || ""
                    );


                const partylist =
                    originalCandidate?.partylist ||
                    "Independent";


                /*
                   Winner box
                */

                const winnerBox =
                    document.createElement("div");


                winnerBox.style.boxSizing =
                    "border-box";


                winnerBox.style.padding =
                    "20px 15px";


                winnerBox.style.border =
                    "1px solid #E5E7EB";


                winnerBox.style.borderRadius =
                    "18px";


                winnerBox.style.background =
                    "#F9FAFB";


                winnerBox.style.display =
                    "flex";


                winnerBox.style.flexDirection =
                    "column";


                winnerBox.style.alignItems =
                    "center";


                winnerBox.style.justifyContent =
                    "center";


                winnerBox.style.textAlign =
                    "center";


                /*
                   Candidate image
                */

                const candidateImage =
                    document.createElement("img");


                candidateImage.src =
                    image?.src ||
                    DEFAULT_CANDIDATE_IMAGE


                candidateImage.style.width =
                    "115px";


                candidateImage.style.height =
                    "115px";


                candidateImage.style.borderRadius =
                    "16px";


                candidateImage.style.objectFit =
                    "cover";


                candidateImage.style.border =
                    "2px solid #E1E4EC";


                candidateImage.style.background =
                    "#F0F2F7";


                candidateImage.style.marginBottom =
                    "15px";


                candidateImage.onerror =
                    () => {

                        candidateImage.src =
                            DEFAULT_CANDIDATE_IMAGE

                    };


                /*
                   Candidate name
                */

                const candidateName =
                    document.createElement("div");


                candidateName.textContent =
                    name?.textContent ||
                    "Unknown Candidate";


                candidateName.style.fontSize =
                    "17px";


                candidateName.style.fontWeight =
                    "800";


                candidateName.style.lineHeight =
                    "1.2";


                candidateName.style.maxWidth =
                    "190px";


                candidateName.style.marginBottom =
                    "8px";


                /*
                   Position
                */

                const candidatePosition =
                    document.createElement("div");


                candidatePosition.textContent =
                    position?.textContent ||
                    "";


                candidatePosition.style.fontSize =
                    "11px";


                candidatePosition.style.fontWeight =
                    "700";


                candidatePosition.style.textTransform =
                    "uppercase";


                candidatePosition.style.letterSpacing =
                    "1px";


                candidatePosition.style.marginBottom =
                    "5px";


                /*
                   Partylist
                */

                const candidatePartylist =
                    document.createElement("div");


                candidatePartylist.textContent =
                    partylist;


                candidatePartylist.style.fontSize =
                    "12px";


                candidatePartylist.style.color =
                    "#6B7280";


                candidatePartylist.style.lineHeight =
                    "1.3";


                candidatePartylist.style.maxWidth =
                    "190px";


                winnerBox.appendChild(
                    candidateImage
                );


                winnerBox.appendChild(
                    candidateName
                );


                winnerBox.appendChild(
                    candidatePosition
                );


                winnerBox.appendChild(
                    candidatePartylist
                );


                grid.appendChild(
                    winnerBox
                );

            }
        );


        exportCard.appendChild(
            grid
        );


        /*
           FOOTER
        */

        const footer =
            document.createElement("div");


        footer.textContent =
            "LCCast • Official Election Results";


        footer.style.textAlign =
            "center";


        footer.style.marginTop =
            "20px";


        footer.style.fontSize =
            "11px";


        footer.style.color =
            "#9CA3AF";


        exportCard.appendChild(
            footer
        );


        document.body.appendChild(
            exportCard
        );


        /*
           Render the dedicated export design.
        */

        const canvas =
            await html2canvas(
                exportCard,
                {
                    backgroundColor: "#FFFFFF",
                    scale: 2,
                    useCORS: true,
                    width: 1200,
                    height: 900
                }
            );


        document.body.removeChild(
            exportCard
        );


        /*
           Final output is exactly 4:3.
        */

        const outputCanvas =
            document.createElement("canvas");


        outputCanvas.width = 1200;

        outputCanvas.height = 900;


        const context =
            outputCanvas.getContext("2d");


        context.drawImage(
            canvas,
            0,
            0,
            1200,
            900
        );


        /*
           Download.
        */

        const link =
            document.createElement("a");


        link.download =
            `${campusName}-winners.png`
                .replace(/\s+/g, "-")
                .toLowerCase();


        link.href =
            outputCanvas.toDataURL(
                "image/png"
            );


        link.click();


    } catch (error) {

        console.error(
            "Winner card download failed:",
            error
        );

    }

}

/* =========================================================
   BUILD DESIGNED POSITION EXPORT
========================================================= */

function buildPositionWinnerExportCard(
    positionName,
    candidates,
    campusName,
    electionStatus
) {

    const orderedCandidates =
        [...candidates].sort(
            (a, b) =>
                Number(b.votes || 0) -
                Number(a.votes || 0)
        );


    const totalVotes =
        orderedCandidates.reduce(
            (total, candidate) =>
                total +
                Number(candidate.votes || 0),
            0
        );


    const exportCard =
        document.createElement("div");


    exportCard.className =
        "position-winner-export-card";


    exportCard.innerHTML = `

        <!-- ============================================
             BACKGROUND
        ============================================= -->

        <div class="position-export-decoration decoration-one"></div>

        <div class="position-export-decoration decoration-two"></div>


        <!-- ============================================
             HEADER
        ============================================= -->

        <div class="position-export-header">

            <div>

                <span class="position-export-brand">
                    LCCAST
                </span>


                <span class="position-export-type">
                    OFFICIAL ELECTION RESULT
                </span>

            </div>


            <span class="position-export-status">

                ${electionStatus === "concluded"

            ? "CONCLUDED"

            : electionStatus.toUpperCase()
        }

            </span>

        </div>


        <!-- ============================================
             POSITION
        ============================================= -->

        <div class="position-export-position">

            <span>
                POSITION
            </span>


            <h1>
                ${escapeHTML(positionName)}
            </h1>

        </div>


        <!-- ============================================
             CANDIDATES
        ============================================= -->

        <div class="position-export-candidates">

            ${orderedCandidates.map(
            (candidate, index) => {

                const votes =
                    Number(
                        candidate.votes || 0
                    );


                const percentage =
                    totalVotes > 0

                        ? (
                            votes /
                            totalVotes
                        ) * 100

                        : 0;


                return `

                            <div
                                class="
                                    position-export-candidate
                                    ${index === 0

                        ? "is-leading"

                        : ""
                    }
                                "
                            >

                                ${index === 0

                        ? `

                                            <div
                                                class="
                                                    position-export-leading-label
                                                "
                                            >

                                                <i
                                                    class="
                                                        bi
                                                        bi-trophy-fill
                                                    "
                                                ></i>

                                                ${electionStatus ===
                            "concluded"

                            ? "OFFICIAL WINNER"

                            : "LEADING CANDIDATE"
                        }

                                            </div>

                                        `

                        : ""
                    }


                                <div
                                    class="
                                        position-export-candidate-photo
                                    "
                                >

                                    <img
                                        src="${resolveCandidateImage(candidate.photo)}"
                                        alt="${escapeHTML(candidate.name || "Candidate")}"
                                    ></img>

                                </div>


                                <div
                                    class="
                                        position-export-candidate-info
                                    "
                                >

                                    <h2>

                                        ${escapeHTML(
                        candidate.name ||
                        "Unknown Candidate"
                    )
                    }

                                    </h2>


                                    <p>

                                        ${escapeHTML(
                        candidate.partylist ||
                        "Independent"
                    )
                    }

                                    </p>


                                    <div
                                        class="
                                            position-export-candidate-stats
                                        "
                                    >

                                        <div>

                                            <strong>
                                                ${votes.toLocaleString()}
                                            </strong>

                                            <span>
                                                VOTES
                                            </span>

                                        </div>


                                        <div>

                                            <strong>
                                                ${percentage.toFixed(1)}%
                                            </strong>

                                            <span>
                                                VOTE SHARE
                                            </span>

                                        </div>

                                    </div>

                                </div>

                            </div>

                        `;

            }
        ).join("")

        }

        </div>


        <!-- ============================================
             FOOTER
        ============================================= -->

        <div class="position-export-footer">

            <span>
                ${escapeHTML(campusName)}
            </span>


            <span>
                LCCAST • ELECTION RESULTS
            </span>

        </div>

    `;


    exportCard
        .querySelectorAll("img")
        .forEach(
            image => {

                image.onerror = () => {

                    image.src =
                        DEFAULT_CANDIDATE_IMAGE

                };

            }
        );


    return exportCard;

}


/* =========================================================
   DOWNLOAD DESIGNED POSITION WINNER PNG
========================================================= */

async function downloadPositionWinnerCard(
    element,
    positionName,
    prefix,
    candidates
) {

    if (
        !element ||
        typeof html2canvas === "undefined"
    ) {
        return;
    }


    /*
       Always use the actual ordered
       candidate data passed from
       renderPositionWinnerCards().
    */

    if (
        !candidates ||
        !candidates.length
    ) {
        return;
    }


    const parent =
        element.closest(
            ".result-entity-container"
        );


    const campusName =

        parent?.querySelector(
            ".department-campus-label"
        )?.textContent
            ?.replace(
                /Campus/i,
                ""
            )
            .trim()

        ||

        parent?.querySelector(
            ".result-entity-title-row h2"
        )?.textContent
            ?.trim()

        ||

        "Campus";


    const exportCard =
        buildPositionWinnerExportCard(

            positionName,

            candidates,

            campusName,

            "concluded"

        );


    exportCard.style.position =
        "fixed";


    exportCard.style.left =
        "-100000px";


    exportCard.style.top =
        "0";


    document.body.appendChild(
        exportCard
    );


    try {

        const canvas =
            await html2canvas(

                exportCard,

                {

                    width: 1200,

                    height: 900,

                    scale: 1,

                    backgroundColor: "#FFFFFF",

                    useCORS: true

                }

            );


        const link =
            document.createElement("a");


        link.download =

            `${prefix}-${positionName}-result.png`

                .replace(
                    /\s+/g,
                    "-"
                )

                .toLowerCase();


        link.href =
            canvas.toDataURL(
                "image/png"
            );


        link.click();

    } catch (error) {

        console.error(

            "Position winner export failed:",

            error

        );

    } finally {

        exportCard.remove();

    }

}

/* =========================================================
   FIND CANDIDATE BY NAME
========================================================= */

function findCandidateByName(name) {

    const searchName =
        String(name)
            .trim()
            .toLowerCase();

    /*
       Search departments.
    */

        const departments =
            liveResultsData?.departments || [];


        for (
            const department of
            departments
        ) {

        for (
            const candidates of
            Object.values(
                department.positions || {}
            )
        ) {

            const candidate =
                candidates.find(
                    item =>
                        String(item.name)
                            .trim()
                            .toLowerCase() ===
                        searchName
                );


            if (candidate) {
                return candidate;
            }

        }

    }


    return null;

}

/* =========================================================
   HTML ESCAPE
========================================================= */

function escapeHTML(value) {

    return String(value)
        .replace(
            /&/g,
            "&amp;"
        )
        .replace(
            /</g,
            "&lt;"
        )
        .replace(
            />/g,
            "&gt;"
        )
        .replace(
            /"/g,
            "&quot;"
        )
        .replace(
            /'/g,
            "&#039;"
        );

}

