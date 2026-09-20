let successToast, successToastTitle, successToastMessage, successToastClose;
let successToastTimer = null;
let actionLoadingModal;
let actionLoadingTitle;
let actionLoadingMessage;

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

function destroyChartsWithPrefix(prefix) {
  Object.keys(charts).forEach((key) => {
    if (key.startsWith(prefix)) {
      charts[key].destroy();
      delete charts[key];
    }
  });
}

async function prepareChartForExport(chart) {
  if (!chart) return;

  chart.stop();
  chart.options.animation = false;
  chart.resize();
  chart.update("none");

  await new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(resolve)),
  );
}

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

function calculateImageHeight(canvas, width) {
  if (!canvas.width) return 80;
  return (canvas.height / canvas.width) * width;
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

function initializeSuccessToast() {
  successToast = document.getElementById("successToast");
  successToastTitle = document.getElementById("successToastTitle");
  successToastMessage = document.getElementById("successToastMessage");
  successToastClose = document.getElementById("successToastClose");

  successToastClose?.addEventListener("click", hideSuccessToast);
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

document.addEventListener("DOMContentLoaded", initializeAnalytics);

const charts = {};
let stompClient = null;
let currentData = null;

async function initializeAnalytics() {
  try {
    initializeSuccessToast();
    initializeActionLoadingModal();
    initializeExportButton();

    showAnalyticsContentSkeleton(document.getElementById("departmentCampusAnalytics"));

    await loadAndRenderAnalytics();
    connectAnalyticsSocket();
  } catch (error) {
    console.error("Failed to initialize analytics:", error);
    const container = document.getElementById("departmentCampusAnalytics");
    if (container) container.innerHTML = `<p class="no-election-message">Unable to load analytics right now.</p>`;
  }
}

async function loadAndRenderAnalytics() {
  const res = await fetch("/api/admin-dept/analytics");
  if (!res.ok) throw new Error("Failed to load analytics");
  currentData = await res.json();
  renderDepartmentAnalytics(currentData);
}

function connectAnalyticsSocket() {
  const socket = new SockJS("/ws-analytics");
  stompClient = new StompJs.Client({
    webSocketFactory: () => socket,
    reconnectDelay: 3000,
  });

  stompClient.onConnect = () => {
    // Payload is the superadmin-wide shape; we just use the message as a refresh signal.
    stompClient.subscribe("/topic/analytics", async () => {
      const scrollY = window.scrollY;
      await loadAndRenderAnalytics();
      requestAnimationFrame(() => window.scrollTo(0, scrollY));
    });
  };

  stompClient.activate();
}

function renderDepartmentAnalytics(data) {
  destroyChartsWithPrefix("department");

  const container = document.getElementById("departmentCampusAnalytics");
  if (!container) return;
  container.innerHTML = "";

  if (!data || !data.departmentId) {
    container.innerHTML = `<p class="no-election-message">Your department is not set up yet. Contact your superadmin.</p>`;
    return;
  }

  if (!data.hasActiveElection || !data.analytics) {
    container.innerHTML = `<p class="no-election-message">No active election for your department yet.</p>`;
    return;
  }

  const ca = data.analytics;

  container.innerHTML = `
    <div class="campus-election-section">
        <div class="campus-election-header">
            <div class="campus-election-title">
                <i class="bi bi-geo-alt"></i>
                <div>
                    <h2>${ca.campus}</h2>
                    <p>${data.departmentTitle || data.departmentName} Department Elections</p>
                </div>
            </div>
        </div>

        <div class="election-item expanded">
            <div class="election-item-header">
                <div class="election-title">
                    <i class="bi bi-calendar-event"></i>
                    <span>${ca.electionTitle}</span>
                </div>
            </div>
            <div class="election-content" style="display:block">

                <div class="analytics-summary-grid">
                    <div class="analytics-summary-card">
                        <div class="summary-icon"><i class="bi bi-people"></i></div>
                        <div><span>Department Voters</span><strong>${ca.totalVoters}</strong></div>
                    </div>
                    <div class="analytics-summary-card">
                        <div class="summary-icon"><i class="bi bi-check2-circle"></i></div>
                        <div><span>Votes Cast</span><strong>${ca.votesCast}</strong></div>
                    </div>
                    <div class="analytics-summary-card">
                        <div class="summary-icon"><i class="bi bi-percent"></i></div>
                        <div><span>Turnout</span><strong>${calculatePercentage(ca.votesCast, ca.totalVoters)}</strong></div>
                    </div>
                    <div class="analytics-summary-card">
                        <div class="summary-icon"><i class="bi bi-person-badge"></i></div>
                        <div><span>Officer Candidates</span><strong>${ca.candidates.length}</strong></div>
                    </div>
                </div>

                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <div>
                            <h3>Department Officer Votes</h3>
                            <p>Votes received by department officer candidates.</p>
                        </div>
                        <button class="graph-export-btn" data-export="departmentCandidateChart"><i class="bi bi-download"></i></button>
                    </div>
                    <div class="chart-container">
                        <canvas id="departmentCandidateChart"></canvas>
                    </div>
                    <div class="candidate-photo-list" id="departmentCandidatePhotos"></div>
                </div>

                <div class="analytics-two-column">
                    <div class="analytics-card">
                        <div class="analytics-card-header">
                            <h3>Voter Turnout</h3>
                            <button class="graph-export-btn" data-export="departmentTurnoutChart"><i class="bi bi-download"></i></button>
                        </div>
                        <div class="chart-container small-chart">
                            <canvas id="departmentTurnoutChart"></canvas>
                        </div>
                    </div>
                    <div class="analytics-card">
                        <div class="analytics-card-header">
                            <h3>Participation by Year Level</h3>
                            <button class="graph-export-btn" data-export="departmentYearChart"><i class="bi bi-download"></i></button>
                        </div>
                        <div class="chart-container small-chart">
                            <canvas id="departmentYearChart"></canvas>
                        </div>
                    </div>
                </div>

                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>Voting Activity</h3>
                        <button class="graph-export-btn" data-export="departmentActivityChart"><i class="bi bi-download"></i></button>
                    </div>
                    <div class="chart-container">
                        <canvas id="departmentActivityChart"></canvas>
                    </div>
                </div>

                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>Voting Status</h3>
                        <button class="graph-export-btn" data-export="departmentStatusChart"><i class="bi bi-download"></i></button>
                    </div>
                    <div class="chart-container chart-container-donut">
                        <canvas id="departmentStatusChart"></canvas>
                    </div>
                </div>

            </div>
        </div>
    </div>
  `;

  createOrUpdateChart("departmentCandidateChart", "bar",
    ca.candidates.map(c => c.name), ca.candidates.map(c => c.votes),
    { title: "Department Officer Votes", horizontal: true });

  const notVoted = Math.max(ca.totalVoters - ca.votesCast, 0);
  createOrUpdateChart("departmentTurnoutChart", "doughnut",
    ["Voted", "Not Yet Voted"], [ca.votesCast, notVoted],
    { title: "Voter Turnout" });

  createOrUpdateChart("departmentYearChart", "bar",
    ca.yearLevel.labels, ca.yearLevel.values,
    { title: "Participation by Year Level" });

  createOrUpdateChart("departmentActivityChart", "line",
    ca.activity.labels, ca.activity.values,
    { title: "Voting Activity" });

  createOrUpdateChart("departmentStatusChart", "doughnut",
    ca.status.labels, ca.status.values,
    { title: "Voting Status" });

  renderCandidatePhotos("departmentCandidatePhotos", ca.candidates);
}

function getChartTitle(chartId) {
  const titles = {
    departmentCandidateChart: "Department Officer Votes",
    departmentTurnoutChart: "Voter Turnout",
    departmentYearChart: "Participation by Year Level",
    departmentActivityChart: "Voting Activity",
    departmentStatusChart: "Voting Status",
  };
  return titles[chartId] || "LCCast Analytics";
}

function initializeExportButton() {
  document.addEventListener("click", async (event) => {
    const button = event.target.closest(".graph-export-btn");
    if (!button) return;
    await exportChartPDF(button.dataset.export);
  });

  document.getElementById("exportDepartment")?.addEventListener("click", exportAllDepartmentPDF);
}

async function exportChartPDF(chartId) {
  const jsPDF = await loadJsPDF();
  const chart = charts[chartId];
  if (!chart) { alert("This graph is not available yet."); return; }

  const canvas = document.getElementById(chartId);
  if (!canvas) { alert("This graph could not be found."); return; }

  await prepareChartForExport(chart);

  const title = getChartTitle(chartId);
  const label = currentData?.departmentTitle || currentData?.departmentName || "";

  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  addPDFHeader(pdf, title, "", label);

  const image = chart.toBase64Image("image/png", 1.0);
  const imageWidth = 180;
  const imageHeight = calculateImageHeight(canvas, imageWidth);
  pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);

  addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);
  addPDFFooter(pdf, label);

  pdf.save(`${sanitizeFilename(title)}.pdf`);
}

async function exportAllDepartmentPDF() {
  const jsPDF = await loadJsPDF();
  const chartIds = [
    "departmentCandidateChart",
    "departmentTurnoutChart",
    "departmentYearChart",
    "departmentActivityChart",
    "departmentStatusChart",
  ];

  const label = currentData?.departmentTitle || currentData?.departmentName || "";
  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  let firstPage = true;

  for (const chartId of chartIds) {
    const chart = charts[chartId];
    const canvas = document.getElementById(chartId);
    if (!chart || !canvas) continue;

    await prepareChartForExport(chart);

    if (!firstPage) pdf.addPage();
    firstPage = false;

    addPDFHeader(pdf, getChartTitle(chartId), "", label);

    const image = chart.toBase64Image("image/png", 1.0);
    const imageWidth = 180;
    const imageHeight = calculateImageHeight(canvas, imageWidth);
    pdf.addImage(image, "PNG", 15, 48, imageWidth, imageHeight);
    addChartValuesToPDF(pdf, chart, 48 + imageHeight + 12);
    addPDFFooter(pdf, label);
  }

  if (firstPage) { alert("No graphs are available for this election."); return; }

  pdf.save(`${sanitizeFilename((currentData?.departmentName || "Department") + "-Graphs")}.pdf`);
}