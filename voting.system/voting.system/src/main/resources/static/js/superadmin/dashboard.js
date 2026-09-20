/* ==========================================================
   LCCast Dashboard
========================================================== */

let liveChart;

/* ==========================================================
   INITIALIZE
========================================================== */

document.addEventListener("DOMContentLoaded", () => {

    setGreeting();
    setCurrentDate();

    setActiveSidebar();

    loadDashboardStatistics();

    initializeCampusSelector();

    initializeElectionTabs();

    initializeChart();

});

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
   (Replace with API later)
========================================================== */

async function loadDashboardStatistics(campus = "all") {

    try {

        const response = await fetch(
            `/superadmin/api/dashboard?campus=${encodeURIComponent(campus)}`
        );

        if (!response.ok) {
            throw new Error(
                `Dashboard request failed: ${response.status}`
            );
        }

        const data = await response.json();

        const statistics = data.statistics;

        initializeCampusChart(data.campusVotes ?? []);

        initializeSSCChart(data.sscVotes ?? []);

        initializeDepartmentChart(data.departmentVotes ?? []);

        const totalVoters =
            statistics.totalVoters ?? 0;

        const totalVoted =
            statistics.totalVoted ?? 0;

        const activeElection =
            statistics.activeElection ?? 0;

        const totalCandidates =
            statistics.totalCandidates ?? 0;

        const turnout =
            totalVoters > 0
                ? Math.round((totalVoted / totalVoters) * 100)
                : 0;


        animateNumber(
            "totalVoters",
            totalVoters
        );

        animateNumber(
            "totalVoted",
            totalVoted
        );

        animateNumber(
            "activeElection",
            activeElection
        );

        animateNumber(
            "totalCandidates",
            totalCandidates
        );

        animateNumber(
            "turnout",
            turnout,
            "%"
        );

    } catch (error) {

        console.error(
            "Failed to load dashboard statistics:",
            error
        );

    }

}

/* =========================================================
   CAMPUS DATA
========================================================= */

const campusData = {

    all: {

        name: "All Campuses",

        description:
            "Overview of voting activity across all campuses."

    },

    college: {

        name: "College",

        description:
            "Voting activity for the College campus."

    },

    // cbas: {

    //     name: "CBAS",

    //     description:
    //         "Voting activity for the CBAS campus."

    // },

    muzon: {

        name: "Muzon",

        description:
            "Voting activity for the Muzon campus."

    },

    // francisco: {

    //     name: "Francisco",

    //     description:
    //         "Voting activity for the Francisco campus."

    // }

};

/* =========================================================
   CHANGE CAMPUS
========================================================= */

function changeCampus(campus) {

    const campusTitle =
        document.getElementById("campusTitle");

    const campusDescription =
        document.getElementById("campusDescription");

    const allCampusContent =
        document.getElementById("allCampusContent");

    const singleCampusContent =
        document.getElementById("singleCampusContent");

    const selectedCampus =
        campusData[campus];

    if (!selectedCampus) return;


    campusTitle.textContent =
        selectedCampus.name;

    campusDescription.textContent =
        selectedCampus.description;


    if (campus === "all") {

        allCampusContent.classList.remove("hidden");

        singleCampusContent.classList.add("hidden");

    } else {

        allCampusContent.classList.add("hidden");

        singleCampusContent.classList.remove("hidden");

    }

}

/* =========================================================
   ELECTION TABS
========================================================= */

function initializeElectionTabs() {

    const tabs =
        document.querySelectorAll(".election-tab");

    tabs.forEach(tab => {

        tab.addEventListener("click", () => {

            tabs.forEach(item => {

                item.classList.remove("active");

            });

            tab.classList.add("active");

            const election =
                tab.dataset.election;

            const sscContent =
                document.getElementById("sscContent");

            const departmentContent =
                document.getElementById("departmentContent");


            if (election === "ssc") {

                sscContent.classList.remove("hidden");

                departmentContent.classList.add("hidden");

            }

            else {

                sscContent.classList.add("hidden");

                departmentContent.classList.remove("hidden");

            }

        });

    });

}

/* =========================================================
   CAMPUS SELECTION
========================================================= */

function initializeCampusSelector() {

    const campusSelect =
        document.getElementById("campusSelect");

    if (!campusSelect) return;

    campusSelect.addEventListener("change", () => {

        const campus = campusSelect.value;

        changeCampus(campus);

        loadDashboardStatistics(campus);

    });

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

        element.textContent =

            Math.floor(progress * end) + suffix;

        if (progress < 1) {

            requestAnimationFrame(step);

        }

    }

    requestAnimationFrame(step);

}

/* ==========================================================
   LIVE GRAPH
========================================================== */

function initializeChart() {

    const canvas = document.getElementById("liveGraph");

    if (!canvas) return;

    if (liveChart) {

        liveChart.destroy();

    }

    liveChart = new Chart(canvas, {

        type: "bar",

        data: {

            labels: [

                "BSIS",
                "BSA",
                "BSBA",
                "BSED",
                "BEED",
                "BSHM"

            ],

            datasets: [{

                label: "Votes",

                data: [

                    235,
                    198,
                    173,
                    156,
                    149,
                    127

                ],

                backgroundColor: "#5B5CEB",

                hoverBackgroundColor: "#494ADB",

                borderRadius: 10,

                borderSkipped: false,

                maxBarThickness: 42

            }]

        },

        options: {

            responsive: true,

            maintainAspectRatio: false,

            animation: {

                duration: 900,

                easing: "easeOutQuart"

            },

            plugins: {

                legend: {

                    display: false

                },

                tooltip: {

                    backgroundColor: "#5B5CEB",

                    displayColors: false,

                    padding: 12,

                    cornerRadius: 10,

                    titleFont: {

                        family: "Poppins",

                        size: 14

                    },

                    bodyFont: {

                        family: "Poppins",

                        size: 13

                    }

                }

            },

            scales: {

                x: {

                    grid: {

                        display: false

                    },

                    ticks: {

                        color: "#64748B",

                        font: {

                            family: "Poppins",

                            size: 13

                        }

                    }

                },

                y: {

                    beginAtZero: true,

                    ticks: {

                        stepSize: 50,

                        color: "#64748B",

                        font: {

                            family: "Poppins",

                            size: 12

                        }

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

/* =========================================================
   CAMPUS GRAPH
========================================================= */

let campusChart;

function initializeCampusChart(campusVotes = []) {

    const canvas = document.getElementById("campusGraph");

    if (!canvas) return;

    if (campusChart) {
        campusChart.destroy();
    }

    const labels = campusVotes.map(item => item.campus);
    const votes = campusVotes.map(item => item.votes);

    campusChart = new Chart(canvas, {

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

                legend: {

                    display: false

                },

                tooltip: {

                    displayColors: false,

                    padding: 12,

                    cornerRadius: 10,

                    callbacks: {

                        label: function (context) {

                            return "Votes: " + context.parsed.y;

                        }

                    }

                }

            },

            scales: {

                x: {

                    grid: {

                        display: false

                    },

                    ticks: {

                        color: "#64748B",

                        font: {

                            family: "Poppins",

                            size: 13

                        }

                    }

                },

                y: {

                    beginAtZero: true,

                    ticks: {

                        precision: 0,

                        color: "#64748B",

                        font: {

                            family: "Poppins",

                            size: 12

                        }

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


/* =========================================================
   SSC GRAPH
========================================================= */

let sscChart;

function initializeSSCChart(sscVotes = []) {

    const canvas =
        document.getElementById("sscGraph");

    if (!canvas) return;

    if (sscChart) {
        sscChart.destroy();
    }

    const labels =
        sscVotes.map(item => item.program);

    const votes =
        sscVotes.map(item => item.votes);

    sscChart = new Chart(canvas, {

        type: "bar",

        data: {

            labels: labels,

            datasets: [{

                label: "Voters",

                data: votes,

                backgroundColor: "#5B5CEB",

                borderRadius: 10,

                borderSkipped: false,

                maxBarThickness: 60

            }]

        },

        options: {

            responsive: true,

            maintainAspectRatio: false,

            plugins: {

                legend: {
                    display: false
                }

            },

            scales: {

                x: {

                    grid: {
                        display: false
                    }

                },

                y: {

                    beginAtZero: true

                }

            }

        }

    });

}

/* =========================================================
   DEPARTMENT GRAPH
========================================================= */

/* =========================================================
   DEPARTMENT ELECTIONS GRAPH
   Uses the same course/program data as the department data
========================================================= */

let departmentChart;

function initializeDepartmentChart(departmentVotes = []) {

    const canvas =
        document.getElementById("departmentGraph");

    if (!canvas) return;

    if (departmentChart) {
        departmentChart.destroy();
    }

    const labels =
        departmentVotes.map(item => item.program);

    const votes =
        departmentVotes.map(item => item.votes);

    departmentChart = new Chart(canvas, {

        type: "bar",

        data: {

            labels: labels,

            datasets: [{

                label: "Votes",

                data: votes,

                backgroundColor: [

                    "#5B5CEB",
                    "#22C55E",
                    "#F59E0B",
                    "#EF4444",
                    "#06B6D4",
                    "#8B5CF6",
                    "#EC4899",
                    "#14B8A6",
                    "#F97316",
                    "#64748B"

                ],

                hoverBackgroundColor: [

                    "#494ADB",
                    "#16A34A",
                    "#D97706",
                    "#DC2626",
                    "#0891B2",
                    "#7C3AED",
                    "#DB2777",
                    "#0D9488",
                    "#EA580C",
                    "#475569"

                ],

                borderRadius: 8,

                borderSkipped: false

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

                legend: {

                    display: false

                },

                tooltip: {

                    displayColors: false,

                    padding: 12,

                    cornerRadius: 10,

                    callbacks: {

                        label: function (context) {

                            return "Votes: " +
                                context.parsed.y;

                        }

                    }

                }

            },

            scales: {

                x: {

                    grid: {

                        display: false

                    },

                    ticks: {

                        color: "#64748B",

                        font: {

                            size: 12

                        }

                    }

                },

                y: {

                    beginAtZero: true,

                    ticks: {

                        color: "#64748B",

                        stepSize: 50

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

/* ==========================================================
   FUTURE API
========================================================== */

// async function loadDashboard() {
//
//     const response = await fetch("/api/dashboard");
//
//     const data = await response.json();
//
// }