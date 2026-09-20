/* ==========================================================
   LCCast Admin-Dept Dashboard
========================================================== */

document.addEventListener("DOMContentLoaded", () => {

    setGreeting();
    setCurrentDate();
    setActiveSidebar();
    loadDashboardStatistics();

});

/* ==========================================================
   GREETING
========================================================== */

function setGreeting() {

    const greetingElement =
        document.getElementById("greetingText");

    if (!greetingElement) return;

    const hour =
        new Date().toLocaleString("en-US", {
            timeZone: "Asia/Manila",
            hour: "numeric",
            hour12: false
        });

    greetingElement.textContent =
        Number(hour) < 12
            ? "Good Morning,"
            : "Good Afternoon,";

}

/* ==========================================================
   CURRENT DATE
========================================================== */

function setCurrentDate() {

    const dateElement = document.getElementById("todayDate");

    if (!dateElement) return;

    const today = new Date();

    dateElement.textContent = today.toLocaleDateString("en-US", {
        weekday: "long",
        month: "long",
        day: "numeric",
        year: "numeric"
    });

}

/* ==========================================================
   ACTIVE SIDEBAR
========================================================== */

function setActiveSidebar() {

    const currentPath = window.location.pathname;

    document.querySelectorAll(".sidebar nav a").forEach(link => {

        link.classList.remove("active");

        if (link.getAttribute("href") === currentPath) {
            link.classList.add("active");
        }

    });

}

/* ==========================================================
   DASHBOARD DATA
   No campus/department params — scope comes entirely from
   the server-side session, never from the client.
========================================================== */

async function loadDashboardStatistics() {

    try {

        const response = await fetch("/admin-dept/api/dashboard");

        if (!response.ok) {
            throw new Error(`Dashboard request failed: ${response.status}`);
        }

        const data = await response.json();
        const statistics = data.statistics;

        initializeDepartmentChart(data.departmentVotes ?? []);

        const totalVoters = statistics.totalVoters ?? 0;
        const totalVoted = statistics.totalVoted ?? 0;
        const activeElection = statistics.activeElection ?? 0;
        const totalCandidates = statistics.totalCandidates ?? 0;

        const turnout =
            totalVoters > 0
                ? Math.round((totalVoted / totalVoters) * 100)
                : 0;

        animateNumber("totalVoters", totalVoters);
        animateNumber("totalVoted", totalVoted);
        animateNumber("activeElection", activeElection);
        animateNumber("totalCandidates", totalCandidates);
        animateNumber("turnout", turnout, "%");

    } catch (error) {

        console.error("Failed to load dashboard statistics:", error);

    }

}

/* ==========================================================
   COUNT ANIMATION
========================================================== */

function animateNumber(id, end, suffix = "", duration = 1000) {

    const element = document.getElementById(id);

    if (!element) return;

    let start = null;

    function step(timestamp) {

        if (!start) start = timestamp;

        const progress = Math.min((timestamp - start) / duration, 1);

        element.textContent = Math.floor(progress * end) + suffix;

        if (progress < 1) {
            requestAnimationFrame(step);
        }

    }

    requestAnimationFrame(step);

}

/* =========================================================
   DEPARTMENT GRAPH
   Votes per position, for this admin's own department only.
========================================================= */

let departmentChart;

function initializeDepartmentChart(departmentVotes = []) {

    const canvas = document.getElementById("departmentGraph");

    if (!canvas) return;

    if (departmentChart) {
        departmentChart.destroy();
    }

    const labels = departmentVotes.map(item => item.position);
    const votes = departmentVotes.map(item => item.votes);

    departmentChart = new Chart(canvas, {

        type: "bar",

        data: {
            labels: labels,
            datasets: [{
                label: "Votes",
                data: votes,
                backgroundColor: "#5B5CEB",
                hoverBackgroundColor: "#494ADB",
                borderRadius: 10,
                borderSkipped: false,
                maxBarThickness: 60
            }]
        },

        options: {

            responsive: true,
            maintainAspectRatio: false,

            animation: {
                duration: 700,
                easing: "easeOutQuart"
            },

            plugins: {
                legend: { display: false },
                tooltip: {
                    displayColors: false,
                    padding: 12,
                    cornerRadius: 10,
                    callbacks: {
                        label: context => "Votes: " + context.parsed.y
                    }
                }
            },

            scales: {
                x: {
                    grid: { display: false },
                    ticks: {
                        color: "#64748B",
                        font: { family: "Poppins", size: 13 }
                    }
                },
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0,
                        color: "#64748B",
                        font: { family: "Poppins", size: 12 }
                    },
                    grid: {
                        color: "#E8EAF2",
                        drawBorder: false
                    }
                }
            }

        }

    });

}