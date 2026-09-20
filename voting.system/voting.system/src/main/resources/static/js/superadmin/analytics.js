/* =========================================================
   LCCAST - ANALYTICS PAGE
   Handles SSC/Department tabs, Chart.js rendering, candidate
   photos, PDF export (single graph / campus / full section).

   NOTE: MOCK_DATA is temporary — replace loadAnalyticsData()
   with a real backend call later.
========================================================= */

document.addEventListener("DOMContentLoaded", initializeAnalytics);

// State
let analyticsData = null;
const charts = {};
let currentDepartment = "BSA";

let stompClient = null;

const expandedElectionIds = new Set();

/* =========================================================
   ANALYTICS — CONTENT SKELETON
========================================================= */

function showAnalyticsContentSkeleton(container) {
  if (!container) return;

  container.innerHTML = `
    <div class="analytics-content-skeleton">

      <div class="analytics-skeleton-summary">
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
      </div>

      <div class="analytics-skeleton-card">
        <div class="skeleton analytics-skeleton-title"></div>
        <div class="skeleton analytics-skeleton-subtitle"></div>
        <div class="skeleton analytics-skeleton-chart"></div>
      </div>

      <div class="analytics-skeleton-card">
        <div class="skeleton analytics-skeleton-title"></div>
        <div class="skeleton analytics-skeleton-subtitle"></div>
        <div class="skeleton analytics-skeleton-chart"></div>
      </div>

      <div class="analytics-skeleton-summary">
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
        <div class="skeleton analytics-skeleton-summary-card"></div>
      </div>

      <div class="analytics-skeleton-card">
        <div class="skeleton analytics-skeleton-title"></div>
        <div class="skeleton analytics-skeleton-subtitle"></div>
        <div class="skeleton analytics-skeleton-chart"></div>
      </div>

    </div>
  `;
}



function connectAnalyticsSocket() {
  const socket = new SockJS("/ws-analytics");
  stompClient = new StompJs.Client({
    webSocketFactory: () => socket,
    reconnectDelay: 3000,
  });

  stompClient.onConnect = () => {
    stompClient.subscribe("/topic/analytics", (message) => {
      analyticsData = JSON.parse(message.body);
      refreshCurrentView();
    });
  };

  stompClient.activate();
}

function refreshCurrentView() {
  const scrollY = window.scrollY;
  const activeTab = document.querySelector(".analytics-tab.active")?.dataset.section;

  if (activeTab === "department") {
    const campus = document.getElementById("departmentCampusSelector")?.value || "all";
    renderDepartment(currentDepartment, campus);
  } else {
    const campus = document.getElementById("sscCampusSelector")?.value || "all";
    renderSSC(campus);
  }

  requestAnimationFrame(() => window.scrollTo(0, scrollY));
}

let successToast, successToastTitle, successToastMessage, successToastClose;
let successToastTimer = null;
let actionLoadingModal;
let actionLoadingTitle;
let actionLoadingMessage;

// Campuses
const CAMPUSES = {
  college: "College",
  muzon: "Muzon",
};


// Departments
let DEPARTMENTS = {};

async function attachDepartmentIds() {
  const res = await fetch("/api/superadmin/analytics/departments");
  if (!res.ok) throw new Error("Failed to load departments");
  const dbDepartments = await res.json();

  DEPARTMENTS = {};
  dbDepartments.forEach((d) => {
    DEPARTMENTS[d.id] = {
      id: d.id,
      name: d.name,
      title: d.title,
      votingType: d.votingType,
    };
  });
}

/* =========================================================
   SSC PROGRAMS
========================================================= */

const SSC_PROGRAMS = [
  "BSIS",
  "BSHM",
  "BSCRIM",
  "BSPSYCH",
  "EDUC",
  "BSBA",
  "BAEL",
  "BSCE",
  "BSA",
  "BSAIS",
];

/* One fixed color for each program.
   These colors remain the same across every campus. */

const SSC_PROGRAM_COLORS = {
  BSIS: "#5B5CEB",
  BSHM: "#22C55E",
  BSCRIM: "#EF4444",
  BSPSYCH: "#8E45F5",
  EDUC: "#F59E0B",
  BSBA: "#06B6D4",
  BAEL: "#EC4899",
  BSCE: "#14B8A6",
  BSA: "#748FEA",
  BSAIS: "#F97316",
};


// Init
async function initializeAnalytics() {
  try {
    initializeSuccessToast();
    initializeActionLoadingModal();

    await attachDepartmentIds();

    // Show skeleton ONLY inside analytics result containers
    showAnalyticsContentSkeleton(
      document.getElementById("sscCampusAnalytics")
    );

    showAnalyticsContentSkeleton(
      document.getElementById("departmentCampusAnalytics")
    );

    analyticsData = await loadAnalyticsData();

    initializeTabs();
    initializeDepartmentSelector();
    initializeCampusSelectors();
    initializeExportButtons();

    renderSSC();

    if (currentDepartment) {
      renderDepartment(currentDepartment);
    }

    connectAnalyticsSocket();

  } catch (error) {
    console.error("Failed to initialize analytics:", error);

    // Remove skeletons if initialization fails
    const sscContainer = document.getElementById("sscCampusAnalytics");
    const departmentContainer = document.getElementById(
      "departmentCampusAnalytics"
    );

    if (sscContainer) sscContainer.innerHTML = "";
    if (departmentContainer) departmentContainer.innerHTML = "";
  }
}

async function loadAnalyticsData() {
  const res = await fetch("/api/superadmin/analytics");
  if (!res.ok) throw new Error("Failed to load analytics");
  return res.json();
}

// Tabs
function initializeTabs() {
  const tabs = document.querySelectorAll(".analytics-tab");
  const sections = document.querySelectorAll(".analytics-section");

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const section = tab.dataset.section;

      tabs.forEach((item) => item.classList.remove("active"));
      sections.forEach((item) => item.classList.remove("active"));
      tab.classList.add("active");

      if (section === "ssc") {
        document.getElementById("sscAnalytics")?.classList.add("active");
      }
      if (section === "department") {
        document.getElementById("departmentAnalytics")?.classList.add("active");
      }
    });
  });
}

// Department selector
function initializeDepartmentSelector() {
  const selector = document.getElementById("departmentSelector");
  if (!selector) return;

  selector.innerHTML = Object.values(DEPARTMENTS)
    .map((d) => `<option value="${d.id}">${d.title}</option>`)
    .join("");

  currentDepartment = Object.keys(DEPARTMENTS)[0] || null;

  selector.addEventListener("change", (event) => {
    currentDepartment = event.target.value;
    const campusSelector = document.getElementById("departmentCampusSelector");
    const campus = campusSelector?.value || "all";
    renderDepartment(currentDepartment, campus);
  });
}

// Campus selectors
function initializeCampusSelectors() {
  const sscSelector = document.getElementById("sscCampusSelector");
  if (sscSelector) {
    sscSelector.addEventListener("change", (event) =>
      renderSSC(event.target.value),
    );
  }

  const departmentCampusSelector = document.getElementById(
    "departmentCampusSelector",
  );
  if (departmentCampusSelector) {
    departmentCampusSelector.addEventListener("change", (event) => {
      renderDepartment(currentDepartment, event.target.value);
    });
  }
}

// SSC
function renderSSC(campusFilter = "all") {
  const container = document.getElementById("sscCampusAnalytics");
  if (!container) return;

  destroyChartsWithPrefix("ssc");
  container.innerHTML = "";

  const campusKeys =
    campusFilter === "all" ? Object.keys(CAMPUSES) : [campusFilter];

  campusKeys.forEach((campusKey) => {
    const data = analyticsData.ssc?.campuses?.[campusKey];
    if (!data) return;

    const section = createCampusElectionSection(campusKey, data, "ssc");
    container.appendChild(section);

    renderSSCCharts(campusKey, data);
  });
}

function createCampusElectionSection(
  campusKey,
  data,
  type,
  departmentCode = null,
) {
  const section = document.createElement("div");
  section.className = "campus-election-section";

    const campusName = CAMPUSES[campusKey];
    const elections = data.electionId
      ? [{ id: data.electionId, title: data.electionTitle }]
      : [];

  section.innerHTML = `
        <div class="campus-election-header">
            <div class="campus-election-title">
                <i class="bi bi-geo-alt"></i>
                <div>
                    <h2>${campusName}</h2>
                    <p>${type === "ssc" ? "Supreme Student Council Elections" : `${DEPARTMENTS[departmentCode]?.title || departmentCode} Department Elections`}</p>
                </div>
            </div>
        </div>

                <div class="campus-election-list">
                    ${elections.length === 0
                      ? `<p class="no-election-message">No active election for this campus yet.</p>`
                      : elections
                      .map(
                        (election) => {
                          const isExpanded = expandedElectionIds.has(election.id);
                          return `
                          <div class="election-item${isExpanded ? " expanded" : ""}" data-election-id="${election.id}">
                              <div class="election-item-header">
                                  <div class="election-title">
                                      <i class="bi bi-calendar-event"></i>
                                      <span>${election.title}</span>
                                  </div>
                                  <div class="election-actions">
                                      <button type="button" class="election-action-btn expand-election" title="Expand Election">
                                          <i class="bi ${isExpanded ? "bi-chevron-up" : "bi-chevron-down"}"></i>
                                      </button>
                                  </div>
                              </div>
                              <div class="election-content" style="display:${isExpanded ? "block" : "none"}">
                                  <div class="election-export-row">
                                      <button
                                          type="button"
                                          class="election-export-btn"
                                          data-campus="${campusKey}"
                                          data-election="${election.id}"
                                          data-type="${type}"
                                          ${departmentCode ? `data-department="${departmentCode}"` : ""}
                                      >
                                          <i class="bi bi-download"></i>
                                          Export Graphs
                                      </button>
                                  </div>
                                  ${createElectionGraphsMarkup(campusKey, data, type, departmentCode)}
                              </div>
                          </div>
                        `;
                        },
                      )
                      .join("")}
                      </div>
    `;

  initializeElectionActions(section);
  return section;
}

// Election graph markup
function createElectionGraphsMarkup(
  campusKey,
  data,
  type,
  departmentCode = null,
) {
  if (type === "ssc") {
    return `
            <div class="analytics-summary-grid">
                <div class="analytics-summary-card">
                    <div class="summary-icon"><i class="bi bi-people"></i></div>
                    <div><span>Total Voters</span><strong>${data.totalVoters}</strong></div>
                </div>
                <div class="analytics-summary-card">
                    <div class="summary-icon"><i class="bi bi-check2-circle"></i></div>
                    <div><span>Votes Cast</span><strong>${data.votesCast}</strong></div>
                </div>
                <div class="analytics-summary-card">
                    <div class="summary-icon"><i class="bi bi-percent"></i></div>
                    <div><span>Turnout</span><strong>${calculatePercentage(data.votesCast, data.totalVoters)}</strong></div>
                </div>
                <div class="analytics-summary-card">
                    <div class="summary-icon"><i class="bi bi-person-badge"></i></div>
                    <div><span>Candidates</span><strong>${data.candidates.length}</strong></div>
                </div>
            </div>

            <div class="analytics-card">
                <div class="analytics-card-header">
                    <div>
                        <h3>Partylist Vote Distribution</h3>
                        <p>Distribution of votes received by each SSC partylist.</p>
                    </div>
                    <button class="graph-export-btn" data-export="sscPartylistChart-${campusKey}"><i class="bi bi-download"></i></button>
                </div>
                <div class="chart-container chart-container-donut">
                    <canvas id="sscPartylistChart-${campusKey}"></canvas>
                </div>
            </div>

            <div class="analytics-card">
                <div class="analytics-card-header">
                    <div>
                        <h3>Candidate Votes</h3>
                        <p>Votes received by individual SSC candidates.</p>
                    </div>
                    <button class="graph-export-btn" data-export="sscCandidateChart-${campusKey}"><i class="bi bi-download"></i></button>
                </div>
                <div class="chart-container">
                    <canvas id="sscCandidateChart-${campusKey}"></canvas>
                </div>
                <div class="candidate-photo-list" id="sscCandidatePhotos-${campusKey}"></div>
            </div>

            <div class="analytics-two-column">
                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>Voter Turnout</h3>
                        <button class="graph-export-btn" data-export="sscTurnoutChart-${campusKey}"><i class="bi bi-download"></i></button>
                    </div>
                    <div class="chart-container small-chart">
                        <canvas id="sscTurnoutChart-${campusKey}"></canvas>
                    </div>
                </div>
                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>Votes by Year Level</h3>
                        <button class="graph-export-btn" data-export="sscYearChart-${campusKey}"><i class="bi bi-download"></i></button>
                    </div>
                    <div class="chart-container small-chart">
                        <canvas id="sscYearChart-${campusKey}"></canvas>
                    </div>
                </div>
            </div>

            <div class="analytics-card">

                <div class="analytics-card-header">

                    <div>

                        <h3>Votes per Program</h3>

                        <p>
                            SSC votes cast by students from each program within this campus.
                        </p>

                    </div>

                    <button
                        class="graph-export-btn"
                        data-export="sscProgramChart-${campusKey}"
                        title="Export Votes per Program">

                        <i class="bi bi-download"></i>

                    </button>

                </div>

                <div class="chart-container ssc-program-chart">

                    <canvas id="sscProgramChart-${campusKey}"></canvas>

                </div>

            </div>

            <div class="analytics-card">
                <div class="analytics-card-header">
                    <h3>Voting Activity</h3>
                    <button class="graph-export-btn" data-export="sscActivityChart-${campusKey}"><i class="bi bi-download"></i></button>
                </div>
                <div class="chart-container">
                    <canvas id="sscActivityChart-${campusKey}"></canvas>
                </div>
            </div>
        `;
  }

  return createDepartmentGraphsMarkup(campusKey, data, departmentCode);
}

// Department graph markup
function createDepartmentGraphsMarkup(campusKey, data, departmentCode) {
  return `
        <div class="analytics-summary-grid">
            <div class="analytics-summary-card">
                <div class="summary-icon"><i class="bi bi-people"></i></div>
                <div><span>Department Voters</span><strong>${data.totalVoters}</strong></div>
            </div>
            <div class="analytics-summary-card">
                <div class="summary-icon"><i class="bi bi-check2-circle"></i></div>
                <div><span>Votes Cast</span><strong>${data.votesCast}</strong></div>
            </div>
            <div class="analytics-summary-card">
                <div class="summary-icon"><i class="bi bi-percent"></i></div>
                <div><span>Turnout</span><strong>${calculatePercentage(data.votesCast, data.totalVoters)}</strong></div>
            </div>
            <div class="analytics-summary-card">
                <div class="summary-icon"><i class="bi bi-person-badge"></i></div>
                <div><span>Officer Candidates</span><strong>${data.candidates.length}</strong></div>
            </div>
        </div>

        <div class="analytics-card">
            <div class="analytics-card-header">
                <div>
                    <h3>Department Officer Votes</h3>
                    <p>Votes received by department officer candidates.</p>
                </div>
                <button class="graph-export-btn" data-export="departmentCandidateChart-${campusKey}"><i class="bi bi-download"></i></button>
            </div>
            <div class="chart-container">
                <canvas id="departmentCandidateChart-${campusKey}"></canvas>
            </div>
            <div class="candidate-photo-list" id="departmentCandidatePhotos-${campusKey}"></div>
        </div>

        <div class="analytics-two-column">
            <div class="analytics-card">
                <div class="analytics-card-header">
                    <h3>Department Voter Turnout</h3>
                    <button class="graph-export-btn" data-export="departmentTurnoutChart-${campusKey}"><i class="bi bi-download"></i></button>
                </div>
                <div class="chart-container small-chart">
                    <canvas id="departmentTurnoutChart-${campusKey}"></canvas>
                </div>
            </div>
            <div class="analytics-card">
                <div class="analytics-card-header">
                    <h3>Participation by Year Level</h3>
                    <button class="graph-export-btn" data-export="departmentYearChart-${campusKey}"><i class="bi bi-download"></i></button>
                </div>
                <div class="chart-container small-chart">
                    <canvas id="departmentYearChart-${campusKey}"></canvas>
                </div>
            </div>
        </div>

        <div class="analytics-card">
            <div class="analytics-card-header">
                <h3>Department Voting Activity</h3>
                <button class="graph-export-btn" data-export="departmentActivityChart-${campusKey}"><i class="bi bi-download"></i></button>
            </div>
            <div class="chart-container">
                <canvas id="departmentActivityChart-${campusKey}"></canvas>
            </div>
        </div>

        <div class="analytics-card">
            <div class="analytics-card-header">
                <h3>Voting Status</h3>
                <button class="graph-export-btn" data-export="departmentStatusChart-${campusKey}"><i class="bi bi-download"></i></button>
            </div>
            <div class="chart-container chart-container-donut">
                <canvas id="departmentStatusChart-${campusKey}"></canvas>
            </div>
        </div>
    `;
}

// Election item actions (expand/delete/archive/export)
function initializeElectionActions(section) {
  section.querySelectorAll(".expand-election").forEach((button) => {
    button.addEventListener("click", () => {
      const election = button.closest(".election-item");
      if (!election) return;

      const electionId = election.dataset.electionId;
      if (electionId) expandedElectionIds.delete(electionId);
      const content = election.querySelector(".election-content");
      const isExpanded = election.classList.toggle("expanded");

      if (isExpanded) {
        content.style.display = "block";
        button.innerHTML = `<i class="bi bi-chevron-up"></i>`;
        expandedElectionIds.add(electionId);
      } else {
        content.style.display = "none";
        button.innerHTML = `<i class="bi bi-chevron-down"></i>`;
        expandedElectionIds.delete(electionId);
      }
    });
  });

  section.querySelectorAll(".election-export-btn").forEach((button) => {
    button.addEventListener("click", async (event) => {
      event.stopPropagation();

      const type = button.dataset.type;
      const campus = button.dataset.campus;

      if (type === "ssc") {
        await exportCampusSSCPDF(campus);
      } else {
        await exportCampusDepartmentPDF(button.dataset.department, campus);
      }
    });
  });
}

function renderSSCCharts(campusKey, data) {

  renderSSCPartylistChart(data, `sscPartylistChart-${campusKey}`);
  renderSSCCandidateChart(data, `sscCandidateChart-${campusKey}`);
  renderSSCTurnoutChart(data, `sscTurnoutChart-${campusKey}`);
  renderSSCYearChart(data, `sscYearChart-${campusKey}`);
  renderSSCActivityChart(data, `sscActivityChart-${campusKey}`);
  renderSSCProgramChart(data, `sscProgramChart-${campusKey}`);
  renderCandidatePhotos(`sscCandidatePhotos-${campusKey}`, data.candidates);
}

// SSC charts
function renderSSCPartylistChart(data, chartId) {
  createOrUpdateChart(
    chartId,
    "doughnut",
    data.partyLists.map((item) => item.name),
    data.partyLists.map((item) => item.votes),
    {
      title: "SSC Partylist Vote Distribution",
      description: "Distribution of votes received by each SSC partylist.",
    },
  );
}

function renderSSCCandidateChart(data, chartId) {
  createOrUpdateChart(
    chartId,
    "bar",
    data.candidates.map((c) => c.name),
    data.candidates.map((c) => c.votes),
    {
      title: "SSC Candidate Votes",
      description: "Votes received by individual SSC candidates.",
      horizontal: true,
    },
  );
}

function renderSSCTurnoutChart(data, chartId) {
  const voted = data.votesCast;
  const notVoted = Math.max(data.totalVoters - voted, 0);

  createOrUpdateChart(
    chartId,
    "doughnut",
    ["Voted", "Not Yet Voted"],
    [voted, notVoted],
    {
      title: "SSC Voter Turnout",
      description:
        "Comparison between registered SSC voters who voted and those who have not yet voted.",
    },
  );
}

function renderSSCYearChart(data, chartId) {
  createOrUpdateChart(
    chartId,
    "bar",
    data.yearLevel.labels,
    data.yearLevel.values,
    {
      title: "SSC Votes by Year Level",
      description:
        "Number of SSC voters who participated from each year level.",
    },
  );
}

function renderSSCProgramChart(data, chartId) {
  const programs = data.programVotes || [];

  createOrUpdateChart(
    chartId,

    "bar",

    programs.map((item) => item.program),

    programs.map((item) => item.votes),

    {
      title: "SSC Votes per Program",

      description:
        "Number of SSC votes cast by students from each academic program.",

      horizontal: true,

      backgroundColor: programs.map((item) => SSC_PROGRAM_COLORS[item.program]),
    },
  );
}

function renderSSCActivityChart(data, chartId) {
  createOrUpdateChart(
    chartId,
    "line",
    data.activity.labels,
    data.activity.values,
    {
      title: "SSC Voting Activity",
      description:
        "Cumulative SSC voting activity throughout the election period.",
    },
  );
}

// Department
function renderDepartment(departmentCode, campusFilter = "all") {
  const container = document.getElementById("departmentCampusAnalytics");
  if (!container) return;

  destroyChartsWithPrefix("department");
  container.innerHTML = "";

  const department = DEPARTMENTS[departmentCode];
  if (!department || !department.id) return;

  const campusKeys =
    campusFilter === "all" ? Object.keys(CAMPUSES) : [campusFilter];

  campusKeys.forEach((campusKey) => {
    const data =
      analyticsData.departments?.departments?.[department.id]?.[campusKey];
    if (!data) return;

    const section = createCampusElectionSection(
      campusKey,
      data,
      "department",
      departmentCode,
    );
    container.appendChild(section);

    renderDepartmentCharts(departmentCode, campusKey, data);
  });
}

function renderDepartmentCharts(departmentCode, campusKey, data) {
  renderDepartmentCandidateChart(
    data,
    departmentCode,
    `departmentCandidateChart-${campusKey}`,
  );
  renderDepartmentTurnoutChart(
    data,
    departmentCode,
    `departmentTurnoutChart-${campusKey}`,
  );
  renderDepartmentYearChart(
    data,
    departmentCode,
    `departmentYearChart-${campusKey}`,
  );
  renderDepartmentActivityChart(
    data,
    departmentCode,
    `departmentActivityChart-${campusKey}`,
  );
  renderDepartmentStatusChart(
    data,
    departmentCode,
    `departmentStatusChart-${campusKey}`,
  );
  renderCandidatePhotos(
    `departmentCandidatePhotos-${campusKey}`,
    data.candidates,
  );
}

// Department charts
function renderDepartmentCandidateChart(data, departmentCode, chartId) {
  const deptTitle = DEPARTMENTS[departmentCode]?.title || departmentCode;
  createOrUpdateChart(
    chartId, "bar",
    data.candidates.map((c) => c.name),
    data.candidates.map((c) => c.votes),
    {
      title: `${deptTitle} Department Officer Votes`,
      description: `Votes received by candidates running for ${deptTitle} department officer positions.`,
      horizontal: true,
    },
  );
}

function renderDepartmentTurnoutChart(data, departmentCode, chartId) {
  const voted = data.votesCast;
  const notVoted = Math.max(data.totalVoters - voted, 0);

  createOrUpdateChart(
    chartId,
    "doughnut",
    ["Voted", "Not Yet Voted"],
    [voted, notVoted],
    {
      title: `${departmentCode} Department Voter Turnout`,
      description: `Turnout among voters belonging to ${departmentCode}.`,
    },
  );
}

function renderDepartmentYearChart(data, departmentCode, chartId) {
  createOrUpdateChart(
    chartId,
    "bar",
    data.yearLevel.labels,
    data.yearLevel.values,
    {
      title: `${departmentCode} Participation by Year Level`,
      description: `Voting participation among ${departmentCode} students by year level.`,
    },
  );
}

function renderDepartmentActivityChart(data, departmentCode, chartId) {
  createOrUpdateChart(
    chartId,
    "line",
    data.activity.labels,
    data.activity.values,
    {
      title: `${departmentCode} Department Voting Activity`,
      description: `Voting activity among ${departmentCode} students throughout the election period.`,
    },
  );
}

function renderDepartmentStatusChart(data, departmentCode, chartId) {
  createOrUpdateChart(
    chartId,
    "doughnut",
    data.status.labels,
    data.status.values,
    {
      title: `${departmentCode} Voting Status`,
      description: `Registered ${departmentCode} voters who have voted and those who have not yet voted.`,
    },
  );
}

// Force a chart to finish rendering before it's captured for export
async function prepareChartForExport(chart) {
  if (!chart) return;

  chart.stop();
  chart.options.animation = false;
  chart.resize();
  chart.update("none");

  // Let the browser actually paint the finished canvas
  await new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(resolve)),
  );
}

function destroyChartsWithPrefix(prefix) {
  Object.keys(charts).forEach((key) => {
    if (key.startsWith(prefix)) {
      charts[key].destroy();
      delete charts[key];
    }
  });
}

// Chart create/update
function createOrUpdateChart(canvasId, type, labels, values, options = {}) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;

  if (!labels || labels.length === 0) {
    if (charts[canvasId]) {
      charts[canvasId].destroy();
      delete charts[canvasId];
    }
    return;
  }

  if (charts[canvasId]) {
    charts[canvasId].destroy();
  }

  const ctx = canvas.getContext("2d");
  const isHorizontal = options.horizontal === true;

  charts[canvasId] = new Chart(ctx, {
    type,
    data: {
      labels,
      datasets: [
        {
          label: options.title || "",
          data: values,
          backgroundColor:
            options.backgroundColor ||
            (type === "doughnut"
              ? [
                  "#748FEA",
                  "#8E45F5",
                  "#5B5CEB",
                  "#22C55E",
                  "#F59E0B",
                  "#EF4444",
                  "#06B6D4",
                  "#EC4899",
                ]
              : "#748FEA"),
          borderColor: type === "line" ? "#5B5CEB" : undefined,
          borderWidth: type === "line" ? 2 : 1,
          borderRadius: type === "bar" ? 6 : 0,
          fill: type === "line" ? false : undefined,
          tension: type === "line" ? 0.35 : 0,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: isHorizontal ? "y" : "x",
      plugins: {
        legend: {
          display: type === "doughnut",
          position: "right",
          labels: { font: { family: "Poppins", size: 12 }, padding: 14 },
        },
        tooltip: {
          callbacks: {
            label: (context) => ` ${context.raw} votes`,
          },
        },
      },
      scales:
        type === "doughnut"
          ? {}
          : {
              x: {
                beginAtZero: true,
                ticks: { font: { family: "Poppins", size: 11 } },
              },
              y: {
                beginAtZero: true,
                ticks: { font: { family: "Poppins", size: 11 } },
              },
            },
    },
  });
}

// Candidate photos (alt text carries the candidate's name)
function renderCandidatePhotos(containerId, candidates) {
  const container = document.getElementById(containerId);
  if (!container) return;

  container.innerHTML = "";

  candidates.forEach((candidate) => {
    const wrapper = document.createElement("div");
    wrapper.className = "candidate-photo";

    const image = document.createElement("img");
    image.src = candidate.photo
      ? `/api/storage/file?path=${encodeURIComponent(candidate.photo)}`
      : "";
    image.alt = candidate.name;
    image.title = candidate.name;

    const label = document.createElement("span");
    label.textContent = candidate.name;

    wrapper.appendChild(image);
    wrapper.appendChild(label);
    container.appendChild(wrapper);
  });
}

// Export buttons
function initializeExportButtons() {
  document.addEventListener("click", async (event) => {
    const button = event.target.closest(".graph-export-btn");
    if (!button) return;

    const chartId = button.dataset.export;
    if (!chartId) return;

    await exportChartPDF(chartId);
  });

  document
    .getElementById("exportSSC")
    ?.addEventListener("click", exportAllSSCPDF);
  document
    .getElementById("exportDepartment")
    ?.addEventListener("click", exportAllDepartmentPDF);
}

// Load jsPDF on demand
let jsPDFPromise = null;

function loadJsPDF() {
  if (window.jspdf) {
    return Promise.resolve(window.jspdf.jsPDF);
  }
  if (jsPDFPromise) {
    return jsPDFPromise;
  }

  jsPDFPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src =
      "https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js";

    script.onload = () => {
      if (window.jspdf?.jsPDF) {
        resolve(window.jspdf.jsPDF);
      } else {
        reject(new Error("jsPDF failed to load."));
      }
    };
    script.onerror = () => reject(new Error("Unable to load jsPDF."));

    document.head.appendChild(script);
  });

  return jsPDFPromise;
}

async function exportChartPDF(
  chartId,
  customTitle = null,
  customDescription = null,
  departmentCode = null,
) {
  const jsPDF = await loadJsPDF();
  const chart = charts[chartId];

  if (!chart) {
    alert("This graph is not available yet.");
    return;
  }

  const canvas = document.getElementById(chartId);
  if (!canvas) {
    alert("This graph could not be found.");
    return;
  }

  // Ensure Chart.js has fully rendered the selected campus chart
  await prepareChartForExport(chart);

  const title = customTitle || getChartTitle(chartId);
  const description = customDescription || getChartDescription(chartId);

  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  addPDFHeader(pdf, title, description);

  // Use the Chart.js image rather than reading the canvas directly
  const image = chart.toBase64Image("image/png", 1.0);
  const imageWidth = 180;
  const imageHeight = calculateImageHeight(canvas, imageWidth);

  pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);

  addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);
  addPDFFooter(pdf, departmentCode);

  pdf.save(`${sanitizeFilename(title)}.pdf`);
}

// Export a single SSC campus
async function exportCampusSSCPDF(campusKey) {
  await prepareChartsForExport("ssc", null, [campusKey]);
  await exportChartsForCampuses("ssc", null, [campusKey]);
}

// Export a single department campus
async function exportCampusDepartmentPDF(departmentCode, campusKey) {
  await prepareChartsForExport("department", departmentCode, [campusKey]);
  await exportChartsForCampuses("department", departmentCode, [campusKey]);
}

// Expand every election in the given campuses and re-render their charts
// so Chart.js has real dimensions to draw into before export.
async function prepareChartsForExport(type, departmentCode, campusKeys) {
  const containerId =
    type === "ssc" ? "sscCampusAnalytics" : "departmentCampusAnalytics";
  const container = document.getElementById(containerId);
  if (!container) return;

  campusKeys.forEach((campusKey) => {
    const electionSection = [
      ...container.querySelectorAll(".campus-election-section"),
    ].find((section) => {
      const heading = section.querySelector(".campus-election-title h2");
      return heading && heading.textContent.trim() === CAMPUSES[campusKey];
    });
    if (!electionSection) return;

    electionSection.querySelectorAll(".election-item").forEach((election) => {
      election.classList.add("expanded");

      const content = election.querySelector(".election-content");
      if (content) content.style.display = "block";

      const expandButton = election.querySelector(".expand-election");
      if (expandButton)
        expandButton.innerHTML = `<i class="bi bi-chevron-up"></i>`;
    });
  });

  // Let display:block apply before Chart.js recalculates canvas sizes
  await new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(resolve)),
  );

  for (const campusKey of campusKeys) {
    const data =
      type === "ssc"
        ? analyticsData.ssc?.campuses?.[campusKey]
        : analyticsData.departments?.departments?.[departmentCode]?.[campusKey]
    if (!data) continue;

        if (!data.candidates || data.candidates.length === 0) continue;

        if (type === "ssc") {
          renderSSCCharts(campusKey, data);
        } else {
          renderDepartmentCharts(departmentCode, campusKey, data);
        }
  }

  // Charts were just recreated (and may be animating) — force them
  // to finish rendering before the PDF is generated.
  const chartTypes =
    type === "ssc"
      ? [
          "sscPartylistChart",
          "sscCandidateChart",
          "sscTurnoutChart",
          "sscYearChart",
          "sscProgramChart",
          "sscActivityChart",
        ]
      : [
          "departmentCandidateChart",
          "departmentTurnoutChart",
          "departmentYearChart",
          "departmentActivityChart",
          "departmentStatusChart",
        ];

  for (const campusKey of campusKeys) {
    for (const chartType of chartTypes) {
      const chart = charts[`${chartType}-${campusKey}`];
      if (chart) await prepareChartForExport(chart);
    }
  }
}

// Export the selected campus(es)/election as one PDF
async function exportChartsForCampuses(type, departmentCode, campusKeys) {
  const jsPDF = await loadJsPDF();

  const chartTypes =
    type === "ssc"
      ? [
          "sscPartylistChart",
          "sscCandidateChart",
          "sscTurnoutChart",
          "sscYearChart",
          "sscProgramChart",
          "sscActivityChart",
        ]
      : [
          "departmentCandidateChart",
          "departmentTurnoutChart",
          "departmentYearChart",
          "departmentActivityChart",
          "departmentStatusChart",
        ];

  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  let firstPage = true;

  for (const campusKey of campusKeys) {
    for (const chartType of chartTypes) {
      const chartId = `${chartType}-${campusKey}`;
      const chart = charts[chartId];
      const canvas = document.getElementById(chartId);

      if (!chart || !canvas) continue;
      if (canvas.width <= 0 || canvas.height <= 0) {
        console.warn(`Skipping invalid chart: ${chartId}`);
        continue;
      }

      await prepareChartForExport(chart);

      if (!firstPage) pdf.addPage();
      firstPage = false;

      const campusName = CAMPUSES[campusKey];

      const title =
        type === "ssc"
          ? `${campusName} — ${getChartTitle(chartId)}`
          : `${campusName} — ${getDepartmentChartTitle(chartId, departmentCode)}`;

      const description =
        type === "ssc"
          ? getChartDescription(chartId)
          : getDepartmentChartDescription(chartId, departmentCode);

      addPDFHeader(
        pdf,
        title,
        description,
        type === "ssc"
          ? `${campusName} — SSC`
          : `${departmentCode} — ${campusName}`,
      );

      const image = chart.toBase64Image("image/png", 1.0);
      const imageWidth = 180;
      const imageHeight = calculateImageHeight(canvas, imageWidth);

      pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);
      addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);

      addPDFFooter(
        pdf,
        type === "ssc"
          ? `${campusName} - SSC`
          : `${departmentCode} - ${campusName}`,
      );
    }
  }

  if (firstPage) {
    alert("No graphs are available for this election.");
    return;
  }

  const filename =
    type === "ssc"
      ? `SSC-Graphs-${CAMPUSES[campusKeys[0]]}`
      : `${departmentCode}-Graphs-${CAMPUSES[campusKeys[0]]}`;

  pdf.save(`${sanitizeFilename(filename)}.pdf`);
}

// Export all SSC graphs for the selected campus (or every campus)
async function exportAllSSCPDF() {
  const campusFilter =
    document.getElementById("sscCampusSelector")?.value || "all";
  const campusKeys =
    campusFilter === "all" ? Object.keys(CAMPUSES) : [campusFilter];

  await prepareChartsForExport("ssc", null, campusKeys);
  await exportChartsForCampuses("ssc", null, campusKeys);
}

// Export all graphs for the selected department + campus (or every campus)
async function exportAllDepartmentPDF() {
  const department = DEPARTMENTS[currentDepartment];
  if (!department) {
    alert("The selected department is not available.");
    return;
  }

  const campusFilter =
    document.getElementById("departmentCampusSelector")?.value || "all";
  const campusKeys =
    campusFilter === "all" ? Object.keys(CAMPUSES) : [campusFilter];

  await prepareChartsForExport("department", currentDepartment, campusKeys);
  await exportChartsForCampuses("department", currentDepartment, campusKeys);
}

// PDF header
function addPDFHeader(pdf, title, description, sectionLabel = null) {
  const pageWidth = pdf.internal.pageSize.getWidth();

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(20);
  pdf.text(title, 15, 20);

  if (sectionLabel) {
    pdf.setFontSize(10);
    pdf.setFont("helvetica", "bold");
    pdf.text(sectionLabel, pageWidth - 15, 20, { align: "right" });
  }

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(10);
  pdf.setTextColor(100, 116, 139);

  const descriptionLines = pdf.splitTextToSize(description || "", 180);
  pdf.text(descriptionLines, 15, 28);

  pdf.setTextColor(30, 41, 59);
  pdf.setFontSize(9);
  pdf.text(`Generated: ${new Date().toLocaleString()}`, 15, 39);

  pdf.setDrawColor(220, 224, 232);
  pdf.line(15, 43, 195, 43);
}

// PDF footer
function addPDFFooter(pdf, label = "") {
  const pageHeight = pdf.internal.pageSize.getHeight();
  const pageWidth = pdf.internal.pageSize.getWidth();

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(8);
  pdf.setTextColor(120, 120, 120);

  pdf.text(
    `LCCast Analytics${label ? " • " + label : ""}`,
    15,
    pageHeight - 10,
  );
  pdf.text("Election Analytics Report", pageWidth - 15, pageHeight - 10, {
    align: "right",
  });

  pdf.setTextColor(30, 41, 59);
}

// Add labels/values table to PDF
function addChartValuesToPDF(pdf, chart, startY) {
  let y = startY;

  const labels = chart.data.labels || [];
  const values = chart.data.datasets?.[0]?.data || [];

  if (!labels.length) return y;

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(11);
  pdf.text("Labels and Values", 15, y);
  y += 8;

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(9);

  labels.forEach((label, index) => {
    const value = values[index] ?? 0;

    if (y > 270) {
      pdf.addPage();
      y = 20;
    }

    pdf.text(`${label}: ${value}`, 20, y);
    y += 5;
  });

  return y;
}

// Chart title/description lookups
function getChartTitle(chartId) {
  const baseChartId = chartId.replace(/-(college|muzon)$/, "");

  const titles = {
    sscPartylistChart: "SSC Partylist Vote Distribution",
    sscCandidateChart: "SSC Candidate Votes",
    sscTurnoutChart: "SSC Voter Turnout",
    sscYearChart: "SSC Votes by Year Level",
    sscActivityChart: "SSC Voting Activity",
    sscProgramChart: "SSC Votes per Program",
    departmentCandidateChart: "Department Officer Votes",
    departmentTurnoutChart: "Department Voter Turnout",
    departmentYearChart: "Participation by Year Level",
    departmentActivityChart: "Department Voting Activity",
    departmentStatusChart: "Voting Status",
  };

  return titles[baseChartId] || "LCCast Analytics";
}

function getChartDescription(chartId) {
  const baseChartId = chartId.replace(/-(college|muzon)$/, "");

  const descriptions = {
    sscPartylistChart: "Distribution of votes received by each SSC partylist.",
    sscCandidateChart: "Votes received by individual SSC candidates.",
    sscTurnoutChart:
      "Comparison between registered SSC voters who voted and those who have not yet voted.",
    sscYearChart: "Number of SSC voters who participated from each year level.",
    sscProgramChart:
      "Number of SSC votes cast by students from each academic program.",
    sscActivityChart:
      "Cumulative SSC voting activity throughout the election period.",
    departmentCandidateChart:
      "Votes received by candidates running for department officer positions.",
    departmentTurnoutChart:
      "Turnout among voters belonging to the selected department.",
    departmentYearChart:
      "Voting participation among students of the selected department by year level.",
    departmentActivityChart:
      "Voting activity among students of the selected department throughout the election period.",
    departmentStatusChart:
      "Registered department voters who have voted and those who have not yet voted.",
  };

  return descriptions[baseChartId] || "";
}

function getDepartmentChartTitle(chartId, departmentCode) {
  const titles = {
    departmentCandidateChart: `${departmentCode} Department Officer Votes`,
    departmentTurnoutChart: `${departmentCode} Department Voter Turnout`,
    departmentYearChart: `${departmentCode} Participation by Year Level`,
    departmentActivityChart: `${departmentCode} Department Voting Activity`,
    departmentStatusChart: `${departmentCode} Voting Status`,
  };

  return titles[chartId] || `${departmentCode} Department Analytics`;
}

function getDepartmentChartDescription(chartId, departmentCode) {
  const descriptions = {
    departmentCandidateChart: `Votes received by candidates running for ${departmentCode} department officer positions.`,
    departmentTurnoutChart: `Turnout only among voters belonging to ${departmentCode}.`,
    departmentYearChart: `Voting participation among ${departmentCode} students by year level.`,
    departmentActivityChart: `Voting activity among ${departmentCode} students throughout the election period.`,
    departmentStatusChart: `Registered ${departmentCode} voters who have voted and those who have not yet voted.`,
  };

  return descriptions[chartId] || "";
}

// Helpers
function calculateImageHeight(canvas, width) {
  if (!canvas.width) return 80;
  return (canvas.height / canvas.width) * width;
}

function updateElement(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function calculatePercentage(value, total) {
  if (!total) return "0%";
  return ((value / total) * 100).toFixed(1) + "%";
}

function sanitizeFilename(filename) {
  return filename
    .replace(/[^a-z0-9\s-_]/gi, "")
    .trim()
    .replace(/\s+/g, "-");
}

// Success toast
function initializeSuccessToast() {
  successToast = document.getElementById("successToast");
  successToastTitle = document.getElementById("successToastTitle");
  successToastMessage = document.getElementById("successToastMessage");
  successToastClose = document.getElementById("successToastClose");

  successToastClose?.addEventListener("click", hideSuccessToast);
}

function initializeActionLoadingModal() {
  actionLoadingModal = document.getElementById("actionLoadingModal");
  actionLoadingTitle = document.getElementById("actionLoadingTitle");
  actionLoadingMessage = document.getElementById("actionLoadingMessage");

  if (!actionLoadingModal) {
    console.error("actionLoadingModal was not found.");
  }
}

function showActionLoading(
  title = "Processing...",
  message = "Please wait while we process your request.",
) {
  if (!actionLoadingModal) return;

  if (actionLoadingTitle) {
    actionLoadingTitle.textContent = title;
  }

  if (actionLoadingMessage) {
    actionLoadingMessage.textContent = message;
  }

  actionLoadingModal.classList.add("show");
  document.body.classList.add("modal-loading");
}

function hideActionLoading() {
  if (!actionLoadingModal) return;

  actionLoadingModal.classList.remove("show");
  document.body.classList.remove("modal-loading");
}

function showSuccessToast(
  title = "Success",
  message = "Action completed successfully.",
) {
  if (!successToast) return;

  if (successToastTimer) clearTimeout(successToastTimer);

  if (successToastTitle) successToastTitle.textContent = title;
  if (successToastMessage) successToastMessage.textContent = message;

  successToast.classList.add("show");

  successToastTimer = setTimeout(hideSuccessToast, 3500);
}

function hideSuccessToast() {
  if (!successToast) return;

  successToast.classList.remove("show");

  if (successToastTimer) {
    clearTimeout(successToastTimer);
    successToastTimer = null;
  }
}
