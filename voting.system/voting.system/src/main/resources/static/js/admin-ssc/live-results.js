/* =========================================================
   LCCAST - ADMIN-SSC LIVE RESULTS
   Scoped to the admin's own campus. SSC only.
========================================================= */

document.addEventListener("DOMContentLoaded", () => {
    initializeLiveResults();
});

/* =========================================================
   GLOBAL STATE
========================================================= */

let liveResultsData = null;
let previousVotes = {};
let stompClient = null;

const ADMIN_CAMPUS_ID = document.body.dataset.adminCampusId || "";
const ADMIN_CAMPUS_NAME = document.body.dataset.adminCampusName || "";

const anonymousCandidateOrder = new Map();

const DEFAULT_CANDIDATE_IMAGE =
    "data:image/svg+xml;charset=UTF-8," +
    encodeURIComponent(`
        <svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
            <rect width="160" height="160" rx="24" fill="#EEF0F5" />
            <circle cx="80" cy="57" r="27" fill="#9CA3AF" />
            <path d="M35 135c4-29 21-45 45-45s41 16 45 45" fill="#9CA3AF" />
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

/* =========================================================
   POSITIONS
========================================================= */

const POSITIONS = [
    { key: "President", label: "President" },
    { key: "VP", label: "VP" },
    { key: "Secretary", label: "Secretary" },
    { key: "Treasurer", label: "Treasurer" },
    { key: "Auditor", label: "Auditor" },
    { key: "PRO Internal", label: "PRO Internal" },
    { key: "PRO External", label: "PRO External" },
];

function getPositionList(positions) {
    const keys = Object.keys(positions || {});
    return keys.map((key) => {
        const standard = POSITIONS.find((p) => p.key === key);
        return standard || { key, label: key };
    });
}

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
            ${[0, 1]
                .map(
                    () => `
                <div class="live-skeleton-candidate">
                    <div class="skeleton live-skeleton-avatar"></div>
                    <div class="live-skeleton-candidate-info">
                        <div class="skeleton live-skeleton-line" style="width:70%"></div>
                        <div class="skeleton live-skeleton-line" style="width:45%"></div>
                        <div class="skeleton live-skeleton-bar"></div>
                    </div>
                </div>
            `
                )
                .join("")}
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
    const container = document.getElementById("sscCampusResultsContainer");
    if (container) container.innerHTML = [0, 1].map(buildSkeletonEntityCard).join("");
}

/* =========================================================
   INITIALIZE
========================================================= */

async function initializeLiveResults() {
    showLiveResultsSkeleton();

    liveResultsData = await loadLiveResultsData();

    initializeFullscreenButtons();
    renderSSC();
    connectLiveResultsSocket();
}

/* =========================================================
   BACKEND DATA LOADER — scoped to this admin's campus
========================================================= */

async function loadLiveResultsData() {
    const response = await fetch("/admin-ssc/live-results/data");
    if (!response.ok) throw new Error("Failed to load live results");
    return await response.json(); // { ssc: [ ...at most one entry... ] }
}

function connectLiveResultsSocket() {
    const socket = new SockJS("/ws-analytics");
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};

    stompClient.connect({}, () => {
        stompClient.subscribe("/topic/live-results", (message) => {
            // The broadcast carries every campus's SSC + department data.
            // Filter down to this admin's own campus, SSC only.
            const fullPayload = JSON.parse(message.body);
            const scoped = (fullPayload?.ssc || []).filter(
                (entity) => entity.campusId === ADMIN_CAMPUS_ID
            );

            liveResultsData = { ssc: scoped };

            updateLiveVotesInPlace(liveResultsData);
            updateLastUpdated();
        });
    });
}

/* =========================================================
   UPDATE LIVE VOTES IN PLACE (no re-render, no scroll jump)
========================================================= */

function updateLiveVotesInPlace(newData) {
    (newData?.ssc || []).forEach((entity) => {

        const cardId = `campus-${entity.id}`;
        const card = document.getElementById(cardId);

        if (!card) {
            if (document.fullscreenElement) return;
            renderSSC();
            return;
        }

        const existingStatusEl = card.querySelector(".election-status");
        const previousPhase = existingStatusEl
            ? [...existingStatusEl.classList].find((c) => c.startsWith("status-"))?.replace("status-", "")
            : null;

        const newPhase = getElectionStatus(entity);

        // Phase changed (e.g. ONGOING -> CONCLUDED): winner cards need
        // to appear/disappear, anonymization needs to be lifted.
        if (previousPhase && previousPhase !== newPhase) {
            if (document.fullscreenElement === card) {
                const newCard = createCampusResultCard(entity.id, entity);
                while (card.firstChild) card.removeChild(card.firstChild);
                while (newCard.firstChild) card.appendChild(newCard.firstChild);
                return;
            }
            renderSSC();
            return;
        }

        const votesCastEl = document.getElementById(`${cardId}-votes-cast`);
        if (votesCastEl) votesCastEl.textContent = Number(entity.votesCast || 0);

        Object.entries(entity.positions || {}).forEach(([positionKey, candidates]) => {
            const positionId = `${entity.id}-${positionKey}`;
            const isAnonymous = false;

            const displayCandidates = getDisplayCandidates(candidates, isAnonymous, positionId);

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
                if (!row) return;

                const votes = Number(displayCandidate.votes || 0);
                const percentage = totalPositionVotes > 0 ? (votes / totalPositionVotes) * 100 : 0;

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

                if (progressBarEl) progressBarEl.style.width = `${percentage}%`;
                if (percentEl) percentEl.textContent = `${percentage.toFixed(1)}%`;
            });
        });
    });
}

/* =========================================================
   FULLSCREEN BUTTONS
========================================================= */

function initializeFullscreenButtons() {
    document.addEventListener("click", async (event) => {
        const button = event.target.closest("[data-fullscreen]");
        if (!button) return;

        const target = document.getElementById(button.dataset.fullscreen);
        if (!target) {
            console.error("Fullscreen target not found:", button.dataset.fullscreen);
            return;
        }

        await enterFullscreen(target);
    });

    document.addEventListener("fullscreenchange", () => {
        updateFullscreenButtons();
    });
}

async function enterFullscreen(element) {
    try {
        if (document.fullscreenElement === element) {
            await document.exitFullscreen();
            return;
        }
        if (document.fullscreenElement) await document.exitFullscreen();
        await element.requestFullscreen();
    } catch (error) {
        console.error("Fullscreen error:", error);
    }
}

function updateFullscreenButtons() {
    const fullscreenElement = document.fullscreenElement;

    document.querySelectorAll("[data-fullscreen]").forEach((button) => {
        const target = document.getElementById(button.dataset.fullscreen);
        const icon = button.querySelector("i");
        const text = button.querySelector("span");

        if (fullscreenElement === target) {
            if (icon) icon.className = "bi bi-fullscreen-exit";
            if (text) text.textContent = "Exit Fullscreen";
        } else {
            if (icon) icon.className = "bi bi-fullscreen";
            if (text) text.textContent = "Fullscreen";
        }
    });
}

/* =========================================================
   RENDER SSC (single campus)
========================================================= */

function renderSSC() {
    const elections = liveResultsData?.ssc || [];
    const container = document.getElementById("sscCampusResultsContainer");
    if (!container) return;

    container.innerHTML = "";

    elections.forEach((election) => {
        container.appendChild(
            createCampusResultCard(election.id, {
                ...election,
                totalVotes: election.votesCast,
            })
        );
    });

    if (elections.length === 0) {
        container.innerHTML = `
            <div class="department-filter-empty">
                <div class="live-empty-icon"><i class="bi bi-search"></i></div>
                <h3>No SSC Election</h3>
                <p>There is no visible SSC election for ${escapeHTML(ADMIN_CAMPUS_NAME) || "your campus"} right now.</p>
            </div>
        `;
    }

    updateElement(
        "sscLiveVotes",
        elections.reduce((total, election) => total + Number(election.votesCast || 0), 0)
    );

    updateLastUpdated();
}

/* =========================================================
   CREATE CAMPUS RESULT CARD
========================================================= */

function createCampusResultCard(campusKey, campus) {
    const card = document.createElement("article");
    const cardId = `campus-${campusKey}`;

    card.id = cardId;
    card.className = "result-entity-container";

    const electionStatus = getElectionStatus(campus);
    const statusLabel =
        { ongoing: "ONGOING", upcoming: "UPCOMING", concluded: "CONCLUDED" }[electionStatus] || "UPCOMING";

    const campusName = campus.campusName || ADMIN_CAMPUS_NAME || "Campus";
    const electionTitle = campus.name || "SSC Election";
    const votesCast = Number(campus.votesCast ?? campus.totalVotes ?? 0);

    card.innerHTML = `
        <div class="result-entity-header">
            <div class="result-entity-identity">
                <span class="result-entity-label">CAMPUS</span>

                <div class="result-entity-title-row">
                    <h2>${escapeHTML(campusName)}</h2>
                    <span class="election-status status-${electionStatus}">${statusLabel}</span>
                </div>

                <p>
                    <strong>${escapeHTML(electionTitle)}</strong><br>
                    ${
                        electionStatus === "ongoing"
                            ? "Voting is currently in progress."
                            : electionStatus === "concluded"
                            ? "Election officially concluded."
                            : "Voting has not started yet."
                    }
                </p>
            </div>

            <div class="result-entity-actions">
                <div class="campus-votes">
                    <span>Votes Cast</span>
                    <strong id="${cardId}-votes-cast">${votesCast}</strong>
                </div>

                ${
                    electionStatus === "ongoing"
                        ? `<button type="button" class="fullscreen-btn" data-fullscreen="${cardId}" title="Fullscreen ${escapeHTML(campusName)}">
                            <i class="bi bi-fullscreen"></i>
                            <span>Fullscreen</span>
                          </button>`
                        : ""
                }
            </div>
        </div>

        <div class="result-entity-section winner-section" id="${cardId}-winner-section"></div>
        <div class="result-entity-section position-winner-section" id="${cardId}-position-section"></div>

        <div class="result-entity-section voting-results-section">
            <div class="result-entity-section-heading">
                <div>
                    <span><i class="bi bi-bar-chart-fill"></i> VOTING RESULTS</span>
                    <p>Live results for every position</p>
                </div>
            </div>
            <div class="positions-grid" id="${cardId}-results"></div>
        </div>
    `;

    if (electionStatus === "concluded") {
        const winnerContainer = card.querySelector(`#${cardId}-winner-section`);
        const winnerSection = document.createElement("section");
        winnerSection.className = "winner-card";

        winnerSection.innerHTML = `
            <div class="winner-card-header">
                <div>
                    <span class="result-section-label"><i class="bi bi-trophy-fill"></i> WINNER CARD</span>
                    <h3>${escapeHTML(campusName)}</h3>
                    <p>Official winners for this election</p>
                </div>
                <button type="button" class="download-winner-btn" data-download-winners="${cardId}-winner">
                    <i class="bi bi-download"></i> Download PNG
                </button>
            </div>

            <div class="winner-card-content" id="${cardId}-winner">
                <div class="winner-card-title">
                    <span>Campus</span>
                    <strong>${escapeHTML(campusName)} Winners!</strong>
                </div>
                <div class="winner-list"></div>
            </div>
        `;

        winnerContainer.appendChild(winnerSection);
        renderWinnerList(winnerSection.querySelector(".winner-list"), campus.positions);

        const positionContainer = card.querySelector(`#${cardId}-position-section`);
        const positionSection = document.createElement("section");
        positionSection.className = "position-winners-section";

        positionSection.innerHTML = `
            <div class="position-winners-header">
                <div>
                    <span>WINNER POSITION CARDS</span>
                    <h3>Position Results</h3>
                    <p>Downloadable result cards for each position</p>
                </div>
            </div>
            <div class="position-winners-grid"></div>
        `;

        positionContainer.appendChild(positionSection);
        renderPositionWinnerCards(
            positionSection.querySelector(".position-winners-grid"),
            campus.positions,
            `ssc-${campusKey}`
        );

        winnerSection.querySelector("[data-download-winners]")?.addEventListener("click", () => {
            downloadWinnerCard(
                winnerSection.querySelector(".winner-card-content"),
                "Supreme Student Council",
                campusName,
                electionTitle
            );
        });
    } else {
        card.querySelector(`#${cardId}-winner-section`)?.remove();
        card.querySelector(`#${cardId}-position-section`)?.remove();
    }

    renderPositionsGrid(
        card.querySelector(".positions-grid"),
        campus.positions,
        campusKey,
        false
    );

    return card;
}

/* =========================================================
   RENDER WINNERS
========================================================= */

function renderWinnerList(container, positions) {
    if (!container) return;
    container.innerHTML = "";

    getPositionList(positions).forEach((position) => {
        const candidates = positions?.[position.key] || [];
        if (!candidates.length) return;

        const winner = [...candidates].sort((a, b) => Number(b.votes || 0) - Number(a.votes || 0))[0];

        const item = document.createElement("div");
        item.className = "winner-item";

        item.innerHTML = `
            <img src="${resolveCandidateImage(winner.photo)}" alt="${escapeHTML(winner.name)}">
            <div class="winner-item-info">
                <span>${escapeHTML(position.label)}</span>
                <strong>${escapeHTML(winner.name)}</strong>
            </div>
            <div class="winner-item-votes">
                <strong>${Number(winner.votes || 0)}</strong>
                <span>Votes</span>
            </div>
        `;

        const image = item.querySelector("img");
        image.onerror = () => { image.src = DEFAULT_CANDIDATE_IMAGE; };

        container.appendChild(item);
    });
}

function renderPositionWinnerCards(container, positions, exportPrefix) {
    if (!container) return;
    container.innerHTML = "";

    getPositionList(positions).forEach((position) => {
        const candidates = positions?.[position.key] || [];
        if (!candidates.length) return;

        const orderedCandidates = [...candidates].sort((a, b) => Number(b.votes || 0) - Number(a.votes || 0));
        const totalVotes = orderedCandidates.reduce((total, c) => total + Number(c.votes || 0), 0);

        const positionCard = document.createElement("article");
        positionCard.className = "position-winner-card";

        positionCard.innerHTML = `
            <div class="position-winner-card-header">
                <div>
                    <span>Position</span>
                    <h3>${escapeHTML(position.label)}</h3>
                </div>
                <button type="button" class="download-position-btn" title="Download ${escapeHTML(position.label)} PNG">
                    <i class="bi bi-download"></i> PNG
                </button>
            </div>
            <div class="position-winner-card-content">
                <div class="position-winner-list"></div>
            </div>
        `;

        const list = positionCard.querySelector(".position-winner-list");

        orderedCandidates.forEach((candidate) => {
            const votes = Number(candidate.votes || 0);
            const percentage = totalVotes > 0 ? (votes / totalVotes) * 100 : 0;

            const item = document.createElement("div");
            item.className = "position-winner-candidate";

            item.innerHTML = `
                <img src="${resolveCandidateImage(candidate.photo)}" alt="${escapeHTML(candidate.name)}">
                <div class="position-winner-info">
                    <strong>${escapeHTML(candidate.name || "Unknown Candidate")}</strong>
                    <span>${escapeHTML(candidate.partylist || "Independent")}</span>
                    <div class="position-winner-progress">
                        <div class="position-winner-progress-track">
                            <div class="position-winner-progress-bar" style="width:${percentage}%"></div>
                        </div>
                        <strong>${percentage.toFixed(1)}%</strong>
                    </div>
                    <small>${votes} vote${votes === 1 ? "" : "s"}</small>
                </div>
            `;

            const image = item.querySelector("img");
            image.onerror = () => { image.src = DEFAULT_CANDIDATE_IMAGE; };

            list.appendChild(item);
        });

        positionCard.querySelector(".download-position-btn")?.addEventListener("click", () => {
            downloadPositionWinnerCard(positionCard, position.label, exportPrefix, orderedCandidates);
        });

        container.appendChild(positionCard);
    });
}

/* =========================================================
   RENDER POSITIONS GRID
========================================================= */

function renderPositionsGrid(container, positions, campusKey, isAnonymous = false) {
    if (!container) return;
    container.innerHTML = "";

    const positionList = Object.keys(positions || {}).map((key) => {
        const standardPosition = POSITIONS.find((p) => p.key === key);
        return standardPosition || { key, label: key };
    });

    positionList.forEach((position) => {
        const originalCandidates = positions?.[position.key] || [];

        const orderedCandidates = getDisplayCandidates(
            originalCandidates,
            isAnonymous,
            `${campusKey}-${position.key}`
        );

        const displayCandidates = isAnonymous
            ? orderedCandidates.map((candidate, index) => createAnonymousCandidate(candidate, index))
            : orderedCandidates;

        container.appendChild(
            createPositionColumn(position, displayCandidates, `${campusKey}-${position.key}`, isAnonymous)
        );
    });
}

/* =========================================================
   GET DISPLAY CANDIDATES (leader always first; others shuffled while ongoing)
========================================================= */

function getDisplayCandidates(candidates, isAnonymous, anonymousKey) {
    if (!candidates || candidates.length === 0) return [];

    if (!isAnonymous) {
        return [...candidates].sort((a, b) => Number(b.votes || 0) - Number(a.votes || 0));
    }

    if (anonymousCandidateOrder.has(anonymousKey)) {
        const savedOrder = anonymousCandidateOrder.get(anonymousKey);

        let currentLeaderIndex = 0;
        for (let i = 1; i < candidates.length; i++) {
            if (Number(candidates[i].votes || 0) > Number(candidates[currentLeaderIndex].votes || 0)) {
                currentLeaderIndex = i;
            }
        }

        const remainingOrder = savedOrder.filter((index) => index !== currentLeaderIndex);
        const finalOrder = [currentLeaderIndex, ...remainingOrder];

        anonymousCandidateOrder.set(anonymousKey, finalOrder);

        return finalOrder.map((index) => candidates[index]).filter(Boolean);
    }

    const indexes = candidates.map((_, index) => index);

    let topIndex = 0;
    for (let i = 1; i < candidates.length; i++) {
        if (Number(candidates[i].votes || 0) > Number(candidates[topIndex].votes || 0)) topIndex = i;
    }

    const remainingIndexes = indexes.filter((index) => index !== topIndex);

    for (let i = remainingIndexes.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [remainingIndexes[i], remainingIndexes[j]] = [remainingIndexes[j], remainingIndexes[i]];
    }

    const finalOrder = [topIndex, ...remainingIndexes];
    anonymousCandidateOrder.set(anonymousKey, finalOrder);

    return finalOrder.map((index) => candidates[index]).filter(Boolean);
}

function createAnonymousCandidate(candidate, anonymousIndex) {
    return {
        id: candidate.id,
        name: `Candidate ${anonymousIndex + 1}`,
        photo: DEFAULT_CANDIDATE_IMAGE,
        partylist: "Anonymous Partylist",
        votes: Number(candidate.votes || 0),
    };
}

/* =========================================================
   CREATE POSITION COLUMN / CANDIDATE ROW
========================================================= */

function createPositionColumn(position, candidates, positionId, isAnonymous = false) {
    const column = document.createElement("div");
    column.className = "position-column";
    column.classList.add(`position-${position.key.toLowerCase().replace(/\s+/g, "-")}`);
    column.dataset.position = position.key;

    const heading = document.createElement("div");
    heading.className = "position-heading";
    heading.textContent = position.label;
    column.appendChild(heading);

    if (!candidates || candidates.length === 0) {
        const empty = document.createElement("div");
        empty.className = "live-no-candidate";
        empty.textContent = "No candidates";
        column.appendChild(empty);
        return column;
    }

    const totalPositionVotes = candidates.reduce((total, c) => total + Number(c.votes || 0), 0);

    candidates.forEach((candidate, index) => {
        const candidateRow = createCandidateRow(candidate, positionId, totalPositionVotes);
        if (index === 0) candidateRow.classList.add("is-leading");
        column.appendChild(candidateRow);
    });

    return column;
}

function createCandidateRow(candidate, positionId, totalPositionVotes) {
    const row = document.createElement("div");
    row.className = "live-candidate";
    row.dataset.candidateId = candidate.id;
    row.dataset.positionId = positionId;

    const image = document.createElement("img");
    image.className = "live-candidate-image";
    image.src = resolveCandidateImage(candidate.photo);
    image.alt = candidate.name || "Candidate";
    image.onerror = () => { image.src = DEFAULT_CANDIDATE_IMAGE; };

    const info = document.createElement("div");
    info.className = "live-candidate-info";

    const name = document.createElement("span");
    name.className = "live-candidate-name";
    name.textContent = candidate.name || "Unknown Candidate";

    const partylist = document.createElement("span");
    partylist.className = "live-candidate-partylist";
    partylist.textContent = candidate.partylist || "Independent";

    info.appendChild(name);
    info.appendChild(partylist);

    const votes = Number(candidate.votes || 0);
    const percentage = totalPositionVotes > 0 ? (votes / totalPositionVotes) * 100 : 0;
    const roundedPercentage = percentage.toFixed(1);

    const progress = document.createElement("div");
    progress.className = "live-candidate-progress";

    const progressTrack = document.createElement("div");
    progressTrack.className = "live-candidate-progress-track";

    const progressBar = document.createElement("div");
    progressBar.className = "live-candidate-progress-bar";
    progressBar.dataset.role = "progress-bar";
    progressBar.style.width = `${percentage}%`;

    const percent = document.createElement("strong");
    percent.className = "live-candidate-percent";
    percent.dataset.role = "percent";
    percent.textContent = `${roundedPercentage}%`;

    progressTrack.appendChild(progressBar);
    progress.appendChild(progressTrack);
    progress.appendChild(percent);

    const voteCount = document.createElement("span");
    voteCount.className = "live-candidate-vote-count";
    voteCount.dataset.role = "vote-count";
    voteCount.textContent = `${votes} vote${votes === 1 ? "" : "s"}`;

    const voteKey = `${positionId}-${candidate.name}`;
    if (previousVotes[voteKey] !== undefined && previousVotes[voteKey] !== votes) {
        voteCount.classList.add("vote-updated");
    }
    previousVotes[voteKey] = votes;

    row.appendChild(image);
    row.appendChild(info);
    row.appendChild(progress);
    row.appendChild(voteCount);

    return row;
}

/* =========================================================
   LAST UPDATED
========================================================= */

function updateLastUpdated() {
    const now = new Date();
    const time = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    updateElement("lastUpdatedSSC", `Updated ${time}`);
}

function updateElement(id, value) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = value;
}

/* =========================================================
   DOWNLOAD WINNER CARD AS 4:3 PNG
========================================================= */

async function downloadWinnerCard(element, title, campusName, subtitle) {
    if (!element || typeof html2canvas === "undefined") return;

    try {
        const winnerItems = element.querySelectorAll(".winner-item");
        if (!winnerItems.length) return;

        const exportCard = document.createElement("div");
        exportCard.style.position = "fixed";
        exportCard.style.left = "-10000px";
        exportCard.style.top = "0";
        exportCard.style.width = "1200px";
        exportCard.style.height = "900px";
        exportCard.style.boxSizing = "border-box";
        exportCard.style.background = "#FFFFFF";
        exportCard.style.padding = "55px 60px";
        exportCard.style.fontFamily = "Arial, Helvetica, sans-serif";
        exportCard.style.color = "#202334";
        exportCard.style.overflow = "hidden";

        const header = document.createElement("div");
        header.style.textAlign = "center";
        header.style.marginBottom = "35px";

        const smallTitle = document.createElement("div");
        smallTitle.textContent = "OFFICIAL ELECTION RESULTS";
        smallTitle.style.fontSize = "14px";
        smallTitle.style.fontWeight = "700";
        smallTitle.style.letterSpacing = "2px";
        smallTitle.style.textTransform = "uppercase";
        smallTitle.style.marginBottom = "8px";

        const mainTitle = document.createElement("div");
        mainTitle.textContent = `${title} Winners`;
        mainTitle.style.fontSize = "38px";
        mainTitle.style.fontWeight = "800";
        mainTitle.style.lineHeight = "1.1";

        const subtitleElement = document.createElement("div");
        subtitleElement.textContent = `${campusName} Campus • ${subtitle}`;
        subtitleElement.style.marginTop = "8px";
        subtitleElement.style.fontSize = "15px";
        subtitleElement.style.color = "#6B7280";

        header.appendChild(smallTitle);
        header.appendChild(mainTitle);
        header.appendChild(subtitleElement);
        exportCard.appendChild(header);

        const grid = document.createElement("div");
        grid.style.display = "grid";
        grid.style.gridTemplateColumns = "repeat(5, 1fr)";
        grid.style.gridTemplateRows = "repeat(2, 1fr)";
        grid.style.gap = "18px";
        grid.style.height = "625px";

        winnerItems.forEach((item) => {
            const image = item.querySelector("img");
            const position = item.querySelector(".winner-item-info span");
            const name = item.querySelector(".winner-item-info strong");

            const originalCandidate = findCandidateByName(name?.textContent || "");
            const partylist = originalCandidate?.partylist || "Independent";

            const winnerBox = document.createElement("div");
            winnerBox.style.boxSizing = "border-box";
            winnerBox.style.padding = "20px 15px";
            winnerBox.style.border = "1px solid #E5E7EB";
            winnerBox.style.borderRadius = "18px";
            winnerBox.style.background = "#F9FAFB";
            winnerBox.style.display = "flex";
            winnerBox.style.flexDirection = "column";
            winnerBox.style.alignItems = "center";
            winnerBox.style.justifyContent = "center";
            winnerBox.style.textAlign = "center";

            const candidateImage = document.createElement("img");
            candidateImage.src = image?.src || DEFAULT_CANDIDATE_IMAGE;
            candidateImage.style.width = "115px";
            candidateImage.style.height = "115px";
            candidateImage.style.borderRadius = "16px";
            candidateImage.style.objectFit = "cover";
            candidateImage.style.border = "2px solid #E1E4EC";
            candidateImage.style.background = "#F0F2F7";
            candidateImage.style.marginBottom = "15px";
            candidateImage.onerror = () => { candidateImage.src = DEFAULT_CANDIDATE_IMAGE; };

            const candidateName = document.createElement("div");
            candidateName.textContent = name?.textContent || "Unknown Candidate";
            candidateName.style.fontSize = "17px";
            candidateName.style.fontWeight = "800";
            candidateName.style.lineHeight = "1.2";
            candidateName.style.maxWidth = "190px";
            candidateName.style.marginBottom = "8px";

            const candidatePosition = document.createElement("div");
            candidatePosition.textContent = position?.textContent || "";
            candidatePosition.style.fontSize = "11px";
            candidatePosition.style.fontWeight = "700";
            candidatePosition.style.textTransform = "uppercase";
            candidatePosition.style.letterSpacing = "1px";
            candidatePosition.style.marginBottom = "5px";

            const candidatePartylist = document.createElement("div");
            candidatePartylist.textContent = partylist;
            candidatePartylist.style.fontSize = "12px";
            candidatePartylist.style.color = "#6B7280";
            candidatePartylist.style.lineHeight = "1.3";
            candidatePartylist.style.maxWidth = "190px";

            winnerBox.appendChild(candidateImage);
            winnerBox.appendChild(candidateName);
            winnerBox.appendChild(candidatePosition);
            winnerBox.appendChild(candidatePartylist);

            grid.appendChild(winnerBox);
        });

        exportCard.appendChild(grid);

        const footer = document.createElement("div");
        footer.textContent = "LCCast • Official Election Results";
        footer.style.textAlign = "center";
        footer.style.marginTop = "20px";
        footer.style.fontSize = "11px";
        footer.style.color = "#9CA3AF";
        exportCard.appendChild(footer);

        document.body.appendChild(exportCard);

        const canvas = await html2canvas(exportCard, {
            backgroundColor: "#FFFFFF",
            scale: 2,
            useCORS: true,
            width: 1200,
            height: 900,
        });

        document.body.removeChild(exportCard);

        const outputCanvas = document.createElement("canvas");
        outputCanvas.width = 1200;
        outputCanvas.height = 900;
        const context = outputCanvas.getContext("2d");
        context.drawImage(canvas, 0, 0, 1200, 900);

        const link = document.createElement("a");
        link.download = `${campusName}-winners.png`.replace(/\s+/g, "-").toLowerCase();
        link.href = outputCanvas.toDataURL("image/png");
        link.click();
    } catch (error) {
        console.error("Winner card download failed:", error);
    }
}

/* =========================================================
   BUILD / DOWNLOAD POSITION WINNER EXPORT CARD
========================================================= */

function buildPositionWinnerExportCard(positionName, candidates, campusName, electionStatus) {
    const orderedCandidates = [...candidates].sort((a, b) => Number(b.votes || 0) - Number(a.votes || 0));
    const totalVotes = orderedCandidates.reduce((total, c) => total + Number(c.votes || 0), 0);

    const exportCard = document.createElement("div");
    exportCard.className = "position-winner-export-card";

    exportCard.innerHTML = `
        <div class="position-export-decoration decoration-one"></div>
        <div class="position-export-decoration decoration-two"></div>

        <div class="position-export-header">
            <div>
                <span class="position-export-brand">LCCAST</span>
                <span class="position-export-type">OFFICIAL ELECTION RESULT</span>
            </div>
            <span class="position-export-status">
                ${electionStatus === "concluded" ? "CONCLUDED" : electionStatus.toUpperCase()}
            </span>
        </div>

        <div class="position-export-position">
            <span>POSITION</span>
            <h1>${escapeHTML(positionName)}</h1>
        </div>

        <div class="position-export-candidates">
            ${orderedCandidates
                .map((candidate, index) => {
                    const votes = Number(candidate.votes || 0);
                    const percentage = totalVotes > 0 ? (votes / totalVotes) * 100 : 0;

                    return `
                        <div class="position-export-candidate ${index === 0 ? "is-leading" : ""}">
                            ${
                                index === 0
                                    ? `<div class="position-export-leading-label">
                                        <i class="bi bi-trophy-fill"></i>
                                        ${electionStatus === "concluded" ? "OFFICIAL WINNER" : "LEADING CANDIDATE"}
                                       </div>`
                                    : ""
                            }
                            <div class="position-export-candidate-photo">
                                <img src="${resolveCandidateImage(candidate.photo)}" alt="${escapeHTML(candidate.name || "Candidate")}"></img>
                            </div>
                            <div class="position-export-candidate-info">
                                <h2>${escapeHTML(candidate.name || "Unknown Candidate")}</h2>
                                <p>${escapeHTML(candidate.partylist || "Independent")}</p>
                                <div class="position-export-candidate-stats">
                                    <div>
                                        <strong>${votes.toLocaleString()}</strong>
                                        <span>VOTES</span>
                                    </div>
                                    <div>
                                        <strong>${percentage.toFixed(1)}%</strong>
                                        <span>VOTE SHARE</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                })
                .join("")}
        </div>

        <div class="position-export-footer">
            <span>${escapeHTML(campusName)}</span>
            <span>LCCAST • ELECTION RESULTS</span>
        </div>
    `;

    exportCard.querySelectorAll("img").forEach((image) => {
        image.onerror = () => { image.src = DEFAULT_CANDIDATE_IMAGE; };
    });

    return exportCard;
}

async function downloadPositionWinnerCard(element, positionName, prefix, candidates) {
    if (!element || typeof html2canvas === "undefined") return;
    if (!candidates || !candidates.length) return;

    const parent = element.closest(".result-entity-container");
    const campusName =
        parent?.querySelector(".result-entity-title-row h2")?.textContent?.trim() || ADMIN_CAMPUS_NAME || "Campus";

    const exportCard = buildPositionWinnerExportCard(positionName, candidates, campusName, "concluded");
    exportCard.style.position = "fixed";
    exportCard.style.left = "-100000px";
    exportCard.style.top = "0";

    document.body.appendChild(exportCard);

    try {
        const canvas = await html2canvas(exportCard, {
            width: 1200,
            height: 900,
            scale: 1,
            backgroundColor: "#FFFFFF",
            useCORS: true,
        });

        const link = document.createElement("a");
        link.download = `${prefix}-${positionName}-result.png`.replace(/\s+/g, "-").toLowerCase();
        link.href = canvas.toDataURL("image/png");
        link.click();
    } catch (error) {
        console.error("Position winner export failed:", error);
    } finally {
        exportCard.remove();
    }
}

/* =========================================================
   FIND CANDIDATE BY NAME (SSC only — no departments here)
========================================================= */

function findCandidateByName(name) {
    const searchName = String(name).trim().toLowerCase();
    const campuses = liveResultsData?.ssc || [];

    for (const campus of campuses) {
        for (const candidates of Object.values(campus.positions || {})) {
            const candidate = candidates.find(
                (item) => String(item.name).trim().toLowerCase() === searchName
            );
            if (candidate) return candidate;
        }
    }

    return null;
}

/* =========================================================
   HTML ESCAPE
========================================================= */

function escapeHTML(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}