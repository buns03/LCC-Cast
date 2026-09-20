/* =========================================================
   LCCAST — ADMIN-SSC ANALYTICS PAGE
   Scoped to the admin's own campus. SSC only.
========================================================= */

document.addEventListener("DOMContentLoaded", initializeAnalytics);

let analyticsData = null;
const charts = {};
let stompClient = null;
const expandedElectionIds = new Set();

const ADMIN_CAMPUS_ID = document.body.dataset.adminCampusId || "";
const ADMIN_CAMPUS_NAME = document.body.dataset.adminCampusName || "";

const SSC_PROGRAM_COLORS = {
  BSIS: "#5B5CEB", BSHM: "#22C55E", BSCRIM: "#EF4444", BSPSYCH: "#8E45F5",
  EDUC: "#F59E0B", BSBA: "#06B6D4", BAEL: "#EC4899", BSCE: "#14B8A6",
  BSA: "#748FEA", BSAIS: "#F97316",
};

let successToast, successToastTitle, successToastMessage, successToastClose;
let successToastTimer = null;

async function initializeAnalytics() {
  try {
    initializeSuccessToast();

    showAnalyticsContentSkeleton(document.getElementById("sscCampusAnalytics"));

    analyticsData = await loadAnalyticsData();

    initializeExportButtons();
    renderSSC();
    connectAnalyticsSocket();
  } catch (error) {
    console.error("Failed to initialize analytics:", error);
    const container = document.getElementById("sscCampusAnalytics");
    if (container) container.innerHTML = "";
  }
}

async function loadAnalyticsData() {
  const res = await fetch("/admin-ssc/api/analytics", { headers: { Accept: "application/json" } });
  if (!res.ok) throw new Error("Failed to load analytics");
  return res.json(); // flat CampusAnalytics
}

function connectAnalyticsSocket() {
  const socket = new SockJS("/ws-analytics");
  stompClient = new StompJs.Client({
    webSocketFactory: () => socket,
    reconnectDelay: 3000,
  });

  stompClient.onConnect = () => {
    // Broadcast carries every campus's data; admin-ssc only needs its own,
    // so just re-fetch the scoped endpoint whenever anything changes.
    stompClient.subscribe("/topic/analytics", async () => {
      try {
        const scrollY = window.scrollY;
        analyticsData = await loadAnalyticsData();
        renderSSC();
        requestAnimationFrame(() => window.scrollTo(0, scrollY));
      } catch (error) {
        console.error("Failed to refresh analytics:", error);
      }
    });
  };

  stompClient.activate();
}

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
    </div>
  `;
}

/* =========================================================
   RENDER — single campus, no selector needed
========================================================= */

function renderSSC() {
  const container = document.getElementById("sscCampusAnalytics");
  if (!container) return;

  destroyChartsWithPrefix("ssc");
  container.innerHTML = "";

  const data = analyticsData;
  if (!data) return;

  const section = createCampusElectionSection(data);
  container.appendChild(section);
  renderSSCCharts(data);
}

function createCampusElectionSection(data) {
  const section = document.createElement("div");
  section.className = "campus-election-section";

  const elections = data.electionId
    ? [{ id: data.electionId, title: data.electionTitle }]
    : [];

  section.innerHTML = `
    <div class="campus-election-header">
        <div class="campus-election-title">
            <i class="bi bi-geo-alt"></i>
            <div>
                <h2>${escapeHtml(ADMIN_CAMPUS_NAME)}</h2>
                <p>Supreme Student Council Elections</p>
            </div>
        </div>
    </div>

    <div class="campus-election-list">
        ${elections.length === 0
          ? `<p class="no-election-message">No active election for this campus yet.</p>`
          : elections.map((election) => {
              const isExpanded = expandedElectionIds.has(election.id);
              return `
              <div class="election-item${isExpanded ? " expanded" : ""}" data-election-id="${election.id}">
                  <div class="election-item-header">
                      <div class="election-title">
                          <i class="bi bi-calendar-event"></i>
                          <span>${escapeHtml(election.title)}</span>
                      </div>
                      <div class="election-actions">
                          <button type="button" class="election-action-btn expand-election" title="Expand Election">
                              <i class="bi ${isExpanded ? "bi-chevron-up" : "bi-chevron-down"}"></i>
                          </button>
                      </div>
                  </div>
                  <div class="election-content" style="display:${isExpanded ? "block" : "none"}">
                      <div class="election-export-row">
                          <button type="button" class="election-export-btn" data-election="${election.id}">
                              <i class="bi bi-download"></i>
                              Export Graphs
                          </button>
                      </div>
                      ${createElectionGraphsMarkup()}
                  </div>
              </div>
            `;
            }).join("")}
    </div>
  `;

  initializeElectionActions(section);
  return section;
}

function createElectionGraphsMarkup() {
  const data = analyticsData;
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
            <button class="graph-export-btn" data-export="sscPartylistChart"><i class="bi bi-download"></i></button>
        </div>
        <div class="chart-container chart-container-donut">
            <canvas id="sscPartylistChart"></canvas>
        </div>
    </div>

    <div class="analytics-card">
        <div class="analytics-card-header">
            <div>
                <h3>Candidate Votes</h3>
                <p>Votes received by individual SSC candidates.</p>
            </div>
            <button class="graph-export-btn" data-export="sscCandidateChart"><i class="bi bi-download"></i></button>
        </div>
        <div class="chart-container">
            <canvas id="sscCandidateChart"></canvas>
        </div>
        <div class="candidate-photo-list" id="sscCandidatePhotos"></div>
    </div>

    <div class="analytics-two-column">
        <div class="analytics-card">
            <div class="analytics-card-header">
                <h3>Voter Turnout</h3>
                <button class="graph-export-btn" data-export="sscTurnoutChart"><i class="bi bi-download"></i></button>
            </div>
            <div class="chart-container small-chart">
                <canvas id="sscTurnoutChart"></canvas>
            </div>
        </div>
        <div class="analytics-card">
            <div class="analytics-card-header">
                <h3>Votes by Year Level</h3>
                <button class="graph-export-btn" data-export="sscYearChart"><i class="bi bi-download"></i></button>
            </div>
            <div class="chart-container small-chart">
                <canvas id="sscYearChart"></canvas>
            </div>
        </div>
    </div>

    <div class="analytics-card">
        <div class="analytics-card-header">
            <div>
                <h3>Votes per Program</h3>
                <p>SSC votes cast by students from each program within this campus.</p>
            </div>
            <button class="graph-export-btn" data-export="sscProgramChart" title="Export Votes per Program">
                <i class="bi bi-download"></i>
            </button>
        </div>
        <div class="chart-container ssc-program-chart">
            <canvas id="sscProgramChart"></canvas>
        </div>
    </div>

    <div class="analytics-card">
        <div class="analytics-card-header">
            <h3>Voting Activity</h3>
            <button class="graph-export-btn" data-export="sscActivityChart"><i class="bi bi-download"></i></button>
        </div>
        <div class="chart-container">
            <canvas id="sscActivityChart"></canvas>
        </div>
    </div>
  `;
}

function initializeElectionActions(section) {
  section.querySelectorAll(".expand-election").forEach((button) => {
    button.addEventListener("click", () => {
      const election = button.closest(".election-item");
      if (!election) return;

      const electionId = election.dataset.electionId;
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
      await exportAllSSCPDF();
    });
  });
}

function renderSSCCharts(data) {
  renderSSCPartylistChart(data);
  renderSSCCandidateChart(data);
  renderSSCTurnoutChart(data);
  renderSSCYearChart(data);
  renderSSCActivityChart(data);
  renderSSCProgramChart(data);
  renderCandidatePhotos("sscCandidatePhotos", data.candidates);
}

function renderSSCPartylistChart(data) {
  createOrUpdateChart("sscPartylistChart", "doughnut",
    data.partyLists.map((i) => i.name), data.partyLists.map((i) => i.votes),
    { title: "SSC Partylist Vote Distribution", description: "Distribution of votes received by each SSC partylist." });
}

function renderSSCCandidateChart(data) {
  createOrUpdateChart("sscCandidateChart", "bar",
    data.candidates.map((c) => c.name), data.candidates.map((c) => c.votes),
    { title: "SSC Candidate Votes", description: "Votes received by individual SSC candidates.", horizontal: true });
}

function renderSSCTurnoutChart(data) {
  const voted = data.votesCast;
  const notVoted = Math.max(data.totalVoters - voted, 0);
  createOrUpdateChart("sscTurnoutChart", "doughnut", ["Voted", "Not Yet Voted"], [voted, notVoted],
    { title: "SSC Voter Turnout", description: "Comparison between registered SSC voters who voted and those who have not yet voted." });
}

function renderSSCYearChart(data) {
  createOrUpdateChart("sscYearChart", "bar", data.yearLevel.labels, data.yearLevel.values,
    { title: "SSC Votes by Year Level", description: "Number of SSC voters who participated from each year level." });
}

function renderSSCProgramChart(data) {
  const programs = data.programVotes || [];
  createOrUpdateChart("sscProgramChart", "bar",
    programs.map((i) => i.program), programs.map((i) => i.votes),
    {
      title: "SSC Votes per Program",
      description: "Number of SSC votes cast by students from each academic program.",
      horizontal: true,
      backgroundColor: programs.map((i) => SSC_PROGRAM_COLORS[i.program]),
    });
}

function renderSSCActivityChart(data) {
  createOrUpdateChart("sscActivityChart", "line", data.activity.labels, data.activity.values,
    { title: "SSC Voting Activity", description: "Cumulative SSC voting activity throughout the election period." });
}

/* =========================================================
   CHART LIFECYCLE (unchanged logic, reused as-is)
========================================================= */

async function prepareChartForExport(chart) {
  if (!chart) return;
  chart.stop();
  chart.options.animation = false;
  chart.resize();
  chart.update("none");
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
}

function destroyChartsWithPrefix(prefix) {
  Object.keys(charts).forEach((key) => {
    if (key.startsWith(prefix)) {
      charts[key].destroy();
      delete charts[key];
    }
  });
}

function createOrUpdateChart(canvasId, type, labels, values, options = {}) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;

  if (!labels || labels.length === 0) {
    if (charts[canvasId]) { charts[canvasId].destroy(); delete charts[canvasId]; }
    return;
  }

  if (charts[canvasId]) charts[canvasId].destroy();

  const ctx = canvas.getContext("2d");
  const isHorizontal = options.horizontal === true;

  charts[canvasId] = new Chart(ctx, {
    type,
    data: {
      labels,
      datasets: [{
        label: options.title || "",
        data: values,
        backgroundColor: options.backgroundColor || (type === "doughnut"
          ? ["#748FEA", "#8E45F5", "#5B5CEB", "#22C55E", "#F59E0B", "#EF4444", "#06B6D4", "#EC4899"]
          : "#748FEA"),
        borderColor: type === "line" ? "#5B5CEB" : undefined,
        borderWidth: type === "line" ? 2 : 1,
        borderRadius: type === "bar" ? 6 : 0,
        fill: type === "line" ? false : undefined,
        tension: type === "line" ? 0.35 : 0,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: isHorizontal ? "y" : "x",
      plugins: {
        legend: { display: type === "doughnut", position: "right", labels: { font: { family: "Poppins", size: 12 }, padding: 14 } },
        tooltip: { callbacks: { label: (context) => ` ${context.raw} votes` } },
      },
      scales: type === "doughnut" ? {} : {
        x: { beginAtZero: true, ticks: { font: { family: "Poppins", size: 11 } } },
        y: { beginAtZero: true, ticks: { font: { family: "Poppins", size: 11 } } },
      },
    },
  });
}

function renderCandidatePhotos(containerId, candidates) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = "";

  candidates.forEach((candidate) => {
    const wrapper = document.createElement("div");
    wrapper.className = "candidate-photo";

    const image = document.createElement("img");
    image.src = candidate.photo ? `/api/storage/file?path=${encodeURIComponent(candidate.photo)}` : "";
    image.alt = candidate.name;
    image.title = candidate.name;

    const label = document.createElement("span");
    label.textContent = candidate.name;

    wrapper.appendChild(image);
    wrapper.appendChild(label);
    container.appendChild(wrapper);
  });
}

/* =========================================================
   EXPORT (PDF) — same as before, but single-campus
========================================================= */

function initializeExportButtons() {
  document.addEventListener("click", async (event) => {
    const button = event.target.closest(".graph-export-btn");
    if (!button) return;
    const chartId = button.dataset.export;
    if (!chartId) return;
    await exportChartPDF(chartId);
  });

  document.getElementById("exportSSC")?.addEventListener("click", exportAllSSCPDF);
}

let jsPDFPromise = null;

function loadJsPDF() {
  if (window.jspdf) return Promise.resolve(window.jspdf.jsPDF);
  if (jsPDFPromise) return jsPDFPromise;

  jsPDFPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js";
    script.onload = () => window.jspdf?.jsPDF ? resolve(window.jspdf.jsPDF) : reject(new Error("jsPDF failed to load."));
    script.onerror = () => reject(new Error("Unable to load jsPDF."));
    document.head.appendChild(script);
  });

  return jsPDFPromise;
}

async function exportChartPDF(chartId) {
  const jsPDF = await loadJsPDF();
  const chart = charts[chartId];
  if (!chart) { alert("This graph is not available yet."); return; }

  const canvas = document.getElementById(chartId);
  if (!canvas) { alert("This graph could not be found."); return; }

  await prepareChartForExport(chart);

  const title = getChartTitle(chartId);
  const description = getChartDescription(chartId);

  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  addPDFHeader(pdf, title, description, ADMIN_CAMPUS_NAME);

  const image = chart.toBase64Image("image/png", 1.0);
  const imageWidth = 180;
  const imageHeight = calculateImageHeight(canvas, imageWidth);

  pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);
  addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);
  addPDFFooter(pdf, ADMIN_CAMPUS_NAME);

  pdf.save(`${sanitizeFilename(title)}.pdf`);
}

async function exportAllSSCPDF() {
  const container = document.getElementById("sscCampusAnalytics");
  container?.querySelectorAll(".election-item").forEach((election) => {
    election.classList.add("expanded");
    const content = election.querySelector(".election-content");
    if (content) content.style.display = "block";
    const btn = election.querySelector(".expand-election");
    if (btn) btn.innerHTML = `<i class="bi bi-chevron-up"></i>`;
  });

  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));

  if (analyticsData?.candidates?.length) renderSSCCharts(analyticsData);

  const chartTypes = ["sscPartylistChart", "sscCandidateChart", "sscTurnoutChart", "sscYearChart", "sscProgramChart", "sscActivityChart"];

  for (const chartType of chartTypes) {
    const chart = charts[chartType];
    if (chart) await prepareChartForExport(chart);
  }

  const jsPDF = await loadJsPDF();
  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  let firstPage = true;

  for (const chartType of chartTypes) {
    const chart = charts[chartType];
    const canvas = document.getElementById(chartType);
    if (!chart || !canvas || canvas.width <= 0 || canvas.height <= 0) continue;

    await prepareChartForExport(chart);

    if (!firstPage) pdf.addPage();
    firstPage = false;

    const title = `${ADMIN_CAMPUS_NAME} — ${getChartTitle(chartType)}`;
    const description = getChartDescription(chartType);

    addPDFHeader(pdf, title, description, `${ADMIN_CAMPUS_NAME} — SSC`);

    const image = chart.toBase64Image("image/png", 1.0);
    const imageWidth = 180;
    const imageHeight = calculateImageHeight(canvas, imageWidth);

    pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);
    addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);
    addPDFFooter(pdf, `${ADMIN_CAMPUS_NAME} - SSC`);
  }

  if (firstPage) { alert("No graphs are available for this election."); return; }

  pdf.save(`${sanitizeFilename(`SSC-Graphs-${ADMIN_CAMPUS_NAME}`)}.pdf`);
}

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

function addPDFFooter(pdf, label = "") {
  const pageHeight = pdf.internal.pageSize.getHeight();
  const pageWidth = pdf.internal.pageSize.getWidth();

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(8);
  pdf.setTextColor(120, 120, 120);

  pdf.text(`LCCast Analytics${label ? " • " + label : ""}`, 15, pageHeight - 10);
  pdf.text("Election Analytics Report", pageWidth - 15, pageHeight - 10, { align: "right" });

  pdf.setTextColor(30, 41, 59);
}

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
    if (y > 270) { pdf.addPage(); y = 20; }
    pdf.text(`${label}: ${value}`, 20, y);
    y += 5;
  });

  return y;
}

function getChartTitle(chartId) {
  const titles = {
    sscPartylistChart: "SSC Partylist Vote Distribution",
    sscCandidateChart: "SSC Candidate Votes",
    sscTurnoutChart: "SSC Voter Turnout",
    sscYearChart: "SSC Votes by Year Level",
    sscActivityChart: "SSC Voting Activity",
    sscProgramChart: "SSC Votes per Program",
  };
  return titles[chartId] || "LCCast Analytics";
}

function getChartDescription(chartId) {
  const descriptions = {
    sscPartylistChart: "Distribution of votes received by each SSC partylist.",
    sscCandidateChart: "Votes received by individual SSC candidates.",
    sscTurnoutChart: "Comparison between registered SSC voters who voted and those who have not yet voted.",
    sscYearChart: "Number of SSC voters who participated from each year level.",
    sscProgramChart: "Number of SSC votes cast by students from each academic program.",
    sscActivityChart: "Cumulative SSC voting activity throughout the election period.",
  };
  return descriptions[chartId] || "";
}

function calculateImageHeight(canvas, width) {
  if (!canvas.width) return 80;
  return (canvas.height / canvas.width) * width;
}

function calculatePercentage(value, total) {
  if (!total) return "0%";
  return ((value / total) * 100).toFixed(1) + "%";
}

function sanitizeFilename(filename) {
  return filename.replace(/[^a-z0-9\s-_]/gi, "").trim().replace(/\s+/g, "-");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function initializeSuccessToast() {
  successToast = document.getElementById("successToast");
  successToastTitle = document.getElementById("successToastTitle");
  successToastMessage = document.getElementById("successToastMessage");
  successToastClose = document.getElementById("successToastClose");
  successToastClose?.addEventListener("click", hideSuccessToast);
}

function showSuccessToast(title = "Success", message = "Action completed successfully.") {
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
  if (successToastTimer) { clearTimeout(successToastTimer); successToastTimer = null; }
}