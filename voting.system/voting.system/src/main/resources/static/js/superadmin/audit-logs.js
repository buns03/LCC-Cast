/* =========================================================
   LCCAST - AUDIT LOGS
========================================================= */

document.addEventListener("DOMContentLoaded", () => {

    initializeAuditLogs();

});


/* =========================================================
   SAMPLE DATA
   Replace this with backend/API data later
========================================================= */

const auditLogs = [

    {
        studentId: "2023-0001",
        name: "Juan Dela Cruz",
        section: "BSIS 3A",
        yearLevel: "3rd Year",
        program: "BSIS",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 08:32 AM"
    },

    {
        studentId: "2023-0002",
        name: "Maria Santos",
        section: "BSA 2A",
        yearLevel: "2nd Year",
        program: "BSA",
        voteType: "Department",
        voteTarget: "BSA",
        status: "Completed",
        timeVoted: "08/13/2026 08:47 AM"
    },

    {
        studentId: "2024-0010",
        name: "Carlos Reyes",
        section: "BSBA 1B",
        yearLevel: "1st Year",
        program: "BSBA",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 09:02 AM"
    },

    {
        studentId: "2022-0044",
        name: "Angela Garcia",
        section: "BSIS 4A",
        yearLevel: "4th Year",
        program: "BSIS",
        voteType: "Department",
        voteTarget: "BSIS",
        status: "Completed",
        timeVoted: "08/13/2026 09:16 AM"
    },

    {
        studentId: "2023-0051",
        name: "Michael Cruz",
        section: "BSCrim 3A",
        yearLevel: "3rd Year",
        program: "BSCrim",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 09:31 AM"
    },

    {
        studentId: "2024-0072",
        name: "Sofia Mendoza",
        section: "BSHM 2A",
        yearLevel: "2nd Year",
        program: "BSHM",
        voteType: "Department",
        voteTarget: "BSHM",
        status: "Partial",
        timeVoted: "08/13/2026 09:48 AM"
    },

    {
        studentId: "2023-0090",
        name: "Daniel Flores",
        section: "BSPSY 3A",
        yearLevel: "3rd Year",
        program: "BSPSY",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 10:03 AM"
    },

    {
        studentId: "2022-0102",
        name: "Gabriel Ramos",
        section: "BSE 4A",
        yearLevel: "4th Year",
        program: "BSE",
        voteType: "Department",
        voteTarget: "BSE",
        status: "Completed",
        timeVoted: "08/13/2026 10:18 AM"
    },

    {
        studentId: "2024-0121",
        name: "Patricia Aquino",
        section: "BSCA 1A",
        yearLevel: "1st Year",
        program: "BSCA",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 10:35 AM"
    },

    {
        studentId: "2023-0145",
        name: "Kevin Bautista",
        section: "BSIS 3B",
        yearLevel: "3rd Year",
        program: "BSIS",
        voteType: "Department",
        voteTarget: "BSIS",
        status: "Completed",
        timeVoted: "08/13/2026 10:51 AM"
    },

    {
        studentId: "2022-0167",
        name: "Andrea Villanueva",
        section: "BSBA 4A",
        yearLevel: "4th Year",
        program: "BSBA",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 11:07 AM"
    },

    {
        studentId: "2024-0182",
        name: "John Paul Torres",
        section: "BSCrim 1A",
        yearLevel: "1st Year",
        program: "BSCrim",
        voteType: "Department",
        voteTarget: "BSCrim",
        status: "Partial",
        timeVoted: "08/13/2026 11:24 AM"
    },

    {
        studentId: "2023-0198",
        name: "Nicole Fernandez",
        section: "BSIS 3A",
        yearLevel: "3rd Year",
        program: "BSIS",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 11:42 AM"
    },

    {
        studentId: "2022-0205",
        name: "Mark Villareal",
        section: "BSHM 4A",
        yearLevel: "4th Year",
        program: "BSHM",
        voteType: "Department",
        voteTarget: "BSHM",
        status: "Completed",
        timeVoted: "08/13/2026 12:01 PM"
    },

    {
        studentId: "2024-0221",
        name: "Christine Navarro",
        section: "BSPSY 1A",
        yearLevel: "1st Year",
        program: "BSPSY",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 12:20 PM"
    },

    {
        studentId: "2023-0240",
        name: "Ryan Castillo",
        section: "BSE 3A",
        yearLevel: "3rd Year",
        program: "BSE",
        voteType: "Department",
        voteTarget: "BSE",
        status: "Completed",
        timeVoted: "08/13/2026 12:38 PM"
    },

    {
        studentId: "2022-0255",
        name: "Elaine Domingo",
        section: "BSA 4A",
        yearLevel: "4th Year",
        program: "BSA",
        voteType: "SSC",
        voteTarget: "Supreme Student Council",
        status: "Completed",
        timeVoted: "08/13/2026 01:02 PM"
    },

    {
        studentId: "2024-0274",
        name: "Francis Lim",
        section: "BSBA 1A",
        yearLevel: "1st Year",
        program: "BSBA",
        voteType: "Department",
        voteTarget: "BSBA",
        status: "Completed",
        timeVoted: "08/13/2026 01:19 PM"
    }

];


/* =========================================================
   STATE
========================================================= */

const auditState = {

    currentPage: 1,

    rowsPerPage: 10,

    search: "",

    yearLevel: "all",

    program: "all",

    voteType: "all",

    status: "all"

};


/* =========================================================
   INITIALIZE
========================================================= */

function initializeAuditLogs() {

    setupSearch();

    setupFilters();

    setupPagination();

    setupClearFilters();

    setupExport();

    renderAuditLogs();

}


/* =========================================================
   ELEMENTS
========================================================= */

function getElements() {

    return {

        search:
            document.getElementById("auditSearch"),

        autocomplete:
            document.getElementById("autocompleteList"),

        yearFilter:
            document.getElementById("yearFilter"),

        programFilter:
            document.getElementById("programFilter"),

        voteTypeFilter:
            document.getElementById("voteTypeFilter"),

        statusFilter:
            document.getElementById("statusFilter"),

        tableBody:
            document.getElementById("auditTableBody"),

        empty:
            document.getElementById("auditEmpty"),

        resultCount:
            document.getElementById("resultCount"),

        pageIndicator:
            document.getElementById("pageIndicator"),

        previousPage:
            document.getElementById("previousPage"),

        nextPage:
            document.getElementById("nextPage"),

        clearFilters:
            document.getElementById("clearFiltersBtn"),

        exportButton:
            document.getElementById("exportAuditBtn")

    };

}


/* =========================================================
   SEARCH
========================================================= */

function setupSearch() {

    const elements = getElements();

    elements.search.addEventListener("input", event => {

        auditState.search =
            event.target.value.trim().toLowerCase();

        auditState.currentPage = 1;

        showAutocomplete();

        renderAuditLogs();

    });


    elements.search.addEventListener("focus", () => {

        showAutocomplete();

    });


    document.addEventListener("click", event => {

        if (
            !elements.search.contains(event.target) &&
            !elements.autocomplete.contains(event.target)
        ) {

            elements.autocomplete.classList.remove("show");

        }

    });

}


/* =========================================================
   AUTOCOMPLETE
========================================================= */

function showAutocomplete() {

    const elements = getElements();

    const query =
        auditState.search.toLowerCase();

    if (!query) {

        elements.autocomplete.classList.remove("show");

        return;

    }


    const matches = auditLogs
        .filter(log => {

            return (
                log.studentId.toLowerCase().includes(query) ||
                log.name.toLowerCase().includes(query) ||
                log.program.toLowerCase().includes(query) ||
                log.section.toLowerCase().includes(query)
            );

        })
        .slice(0, 6);


    if (!matches.length) {

        elements.autocomplete.classList.remove("show");

        return;

    }


    elements.autocomplete.innerHTML = matches
        .map(log => {

            return `

                <div
                    class="autocomplete-item"
                    data-student-id="${escapeAttribute(log.studentId)}"
                >

                    <strong>
                        ${escapeHTML(log.studentId)}
                        — ${escapeHTML(log.name)}
                    </strong>

                    <span>
                        ${escapeHTML(log.program)}
                        ·
                        ${escapeHTML(log.section)}
                        ·
                        ${escapeHTML(log.voteType)}
                    </span>

                </div>

            `;

        })
        .join("");


    elements.autocomplete
        .querySelectorAll(".autocomplete-item")
        .forEach(item => {

            item.addEventListener("click", () => {

                const studentId =
                    item.dataset.studentId;

                const selected =
                    auditLogs.find(
                        log =>
                            log.studentId === studentId
                    );

                if (!selected) return;


                elements.search.value =
                    selected.studentId;


                auditState.search =
                    selected.studentId.toLowerCase();


                elements.autocomplete
                    .classList.remove("show");


                auditState.currentPage = 1;

                renderAuditLogs();

            });

        });


    elements.autocomplete.classList.add("show");

}


/* =========================================================
   FILTERS
========================================================= */

function setupFilters() {

    const elements = getElements();


    elements.yearFilter.addEventListener(
        "change",
        event => {

            auditState.yearLevel =
                event.target.value;

            auditState.currentPage = 1;

            renderAuditLogs();

        }
    );


    elements.programFilter.addEventListener(
        "change",
        event => {

            auditState.program =
                event.target.value;

            auditState.currentPage = 1;

            renderAuditLogs();

        }
    );


    elements.voteTypeFilter.addEventListener(
        "change",
        event => {

            auditState.voteType =
                event.target.value;

            auditState.currentPage = 1;

            renderAuditLogs();

        }
    );


    elements.statusFilter.addEventListener(
        "change",
        event => {

            auditState.status =
                event.target.value;

            auditState.currentPage = 1;

            renderAuditLogs();

        }
    );

}


/* =========================================================
   FILTER DATA
========================================================= */

function getFilteredLogs() {

    return auditLogs.filter(log => {


        const searchMatch =

            !auditState.search ||

            log.studentId
                .toLowerCase()
                .includes(auditState.search) ||

            log.name
                .toLowerCase()
                .includes(auditState.search) ||

            log.program
                .toLowerCase()
                .includes(auditState.search) ||

            log.section
                .toLowerCase()
                .includes(auditState.search);


        const yearMatch =

            auditState.yearLevel === "all" ||

            log.yearLevel ===
                auditState.yearLevel;


        const programMatch =

            auditState.program === "all" ||

            log.program ===
                auditState.program;


        const voteTypeMatch =

            auditState.voteType === "all" ||

            log.voteType ===
                auditState.voteType;


        const statusMatch =

            auditState.status === "all" ||

            log.status ===
                auditState.status;


        return (

            searchMatch &&

            yearMatch &&

            programMatch &&

            voteTypeMatch &&

            statusMatch

        );

    });

}


/* =========================================================
   RENDER
========================================================= */

function renderAuditLogs() {

    const elements = getElements();

    const filteredLogs =
        getFilteredLogs();


    const totalPages = Math.max(

        1,

        Math.ceil(
            filteredLogs.length /
            auditState.rowsPerPage
        )

    );


    if (
        auditState.currentPage >
        totalPages
    ) {

        auditState.currentPage =
            totalPages;

    }


    const startIndex =

        (auditState.currentPage - 1) *
        auditState.rowsPerPage;


    const endIndex =

        startIndex +
        auditState.rowsPerPage;


    const visibleLogs =
        filteredLogs.slice(
            startIndex,
            endIndex
        );


    renderTable(visibleLogs);


    elements.resultCount.textContent =

        `${filteredLogs.length} ${
            filteredLogs.length === 1
                ? "vote log"
                : "vote logs"
        }`;


    elements.pageIndicator.textContent =

        `Page ${auditState.currentPage} / ${totalPages}`;


    elements.previousPage.disabled =

        auditState.currentPage <= 1;


    elements.nextPage.disabled =

        auditState.currentPage >= totalPages;


    if (filteredLogs.length === 0) {

        elements.empty.classList.remove("hidden");

    } else {

        elements.empty.classList.add("hidden");

    }

}


/* =========================================================
   TABLE
========================================================= */

function renderTable(logs) {

    const elements = getElements();


    if (!logs.length) {

        elements.tableBody.innerHTML = "";

        return;

    }


    elements.tableBody.innerHTML = logs
        .map(log => {

            const voteClass =
                log.voteType === "SSC"
                    ? "ssc"
                    : "department";


            return `

                <tr>

                    <td>

                        <div class="student-main">

                            ${escapeHTML(log.studentId)}
                            -
                            ${escapeHTML(log.name)}

                        </div>

                        <div class="student-sub">

                            ${escapeHTML(log.section)}

                        </div>

                    </td>


                    <td>

                        <span
                            class="vote-type ${voteClass}"
                        >

                            <span
                                class="vote-type-dot"
                            ></span>

                            ${
                                log.voteType === "SSC"
                                    ? "SSC"
                                    : `Department — ${escapeHTML(log.voteTarget)}`
                            }

                        </span>

                    </td>


                    <td>

                        <span class="vote-time">

                            ${escapeHTML(log.timeVoted)}

                        </span>

                    </td>

                </tr>

            `;

        })
        .join("");

}


/* =========================================================
   PAGINATION
========================================================= */

function setupPagination() {

    const elements = getElements();


    elements.previousPage.addEventListener(
        "click",
        () => {

            if (auditState.currentPage <= 1) {
                return;
            }

            auditState.currentPage--;

            renderAuditLogs();

        }
    );


    elements.nextPage.addEventListener(
        "click",
        () => {

            const totalPages =
                Math.ceil(
                    getFilteredLogs().length /
                    auditState.rowsPerPage
                );


            if (
                auditState.currentPage >=
                totalPages
            ) {

                return;

            }


            auditState.currentPage++;

            renderAuditLogs();

        }
    );

}


/* =========================================================
   CLEAR FILTERS
========================================================= */

function setupClearFilters() {

    const elements = getElements();


    elements.clearFilters.addEventListener(
        "click",
        () => {

            auditState.search = "";

            auditState.yearLevel = "all";

            auditState.program = "all";

            auditState.voteType = "all";

            auditState.status = "all";

            auditState.currentPage = 1;


            elements.search.value = "";

            elements.yearFilter.value = "all";

            elements.programFilter.value = "all";

            elements.voteTypeFilter.value = "all";

            elements.statusFilter.value = "all";


            elements.autocomplete
                .classList.remove("show");


            renderAuditLogs();

        }
    );

}


/* =========================================================
   EXPORT
========================================================= */

function setupExport() {

    const elements = getElements();


    elements.exportButton.addEventListener(
        "click",
        exportFilteredPDF
    );

}


/* =========================================================
   PDF EXPORT
========================================================= */

function exportFilteredPDF() {

    const filteredLogs =
        getFilteredLogs();


    if (!filteredLogs.length) {

        alert(
            "There are no audit logs to export."
        );

        return;

    }


    const {
        jsPDF
    } = window.jspdf;


    const doc =
        new jsPDF({
            orientation:"landscape",
            unit:"mm",
            format:"a4"
        });


    /* =====================================================
       TITLE
    ====================================================== */

    let reportTitle =
        "LCCast Audit Logs";


    if (
        auditState.voteType ===
        "SSC"
    ) {

        reportTitle =
            "LCCast SSC Vote Logs";

    }


    if (
        auditState.voteType ===
        "Department"
    ) {

        reportTitle =
            "LCCast Department Vote Logs";

    }


    /* =====================================================
       HEADER
    ====================================================== */

    doc.setFont(
        "helvetica",
        "bold"
    );

    doc.setFontSize(20);

    doc.text(
        reportTitle,
        14,
        16
    );


    doc.setFont(
        "helvetica",
        "normal"
    );

    doc.setFontSize(9);


    const generatedDate =
        new Date().toLocaleString(
            "en-PH",
            {
                dateStyle:"medium",
                timeStyle:"short"
            }
        );


    doc.text(
        `Generated: ${generatedDate}`,
        14,
        23
    );


    /* =====================================================
       FILTER SUMMARY
    ====================================================== */

    const filterParts = [];


    if (
        auditState.yearLevel !==
        "all"
    ) {

        filterParts.push(
            `Year: ${auditState.yearLevel}`
        );

    }


    if (
        auditState.program !==
        "all"
    ) {

        filterParts.push(
            `Program: ${auditState.program}`
        );

    }


    if (
        auditState.voteType !==
        "all"
    ) {

        filterParts.push(
            `Vote Type: ${auditState.voteType}`
        );

    }


    if (
        auditState.status !==
        "all"
    ) {

        filterParts.push(
            `Status: ${auditState.status}`
        );

    }


    if (
        auditState.search
    ) {

        filterParts.push(
            `Search: ${auditState.search}`
        );

    }


    const filterText =
        filterParts.length
            ? filterParts.join("  |  ")
            : "All vote logs";


    doc.text(
        `Filters: ${filterText}`,
        14,
        29
    );


    /* =====================================================
       TABLE DATA
    ====================================================== */

    const tableData =
        filteredLogs.map(log => {

            return [

                `${log.studentId} - ${log.name}\n${log.section}`,

                log.voteType === "SSC"
                    ? "SSC"
                    : `Department - ${log.voteTarget}`,

                log.timeVoted,

                log.status

            ];

        });


    /* =====================================================
       PDF TABLE
    ====================================================== */

    doc.autoTable({

        startY:35,

        head:[

            [
                "Student Details",
                "Vote Type",
                "Time Voted",
                "Status"
            ]

        ],

        body:tableData,

        theme:"grid",

        styles:{

            font:"helvetica",

            fontSize:9,

            cellPadding:4,

            valign:"middle",

            lineColor:[190,190,190],

            lineWidth:.25

        },

        headStyles:{

            fillColor:[91,92,235],

            textColor:[255,255,255],

            fontStyle:"bold",

            halign:"left"

        },

        alternateRowStyles:{

            fillColor:[248,249,253]

        },

        columnStyles:{

            0:{
                cellWidth:105
            },

            1:{
                cellWidth:65
            },

            2:{
                cellWidth:55
            },

            3:{
                cellWidth:35
            }

        },

        margin:{

            left:14,

            right:14

        },

        didDrawPage: data => {

            const pageNumber =
                doc.internal.getNumberOfPages();


            doc.setFontSize(8);

            doc.setTextColor(
                120,
                120,
                120
            );


            doc.text(

                `LCCast Audit Logs | Page ${pageNumber}`,

                14,

                doc.internal.pageSize.height - 8

            );

        }

    });


    /* =====================================================
       DOWNLOAD
    ====================================================== */

    const dateString =
        new Date()
            .toISOString()
            .slice(0,10);


    let filename =
        "LCCast-Audit-Logs";


    if (
        auditState.voteType ===
        "SSC"
    ) {

        filename =
            "LCCast-SSC-Vote-Logs";

    }


    if (
        auditState.voteType ===
        "Department"
    ) {

        filename =
            "LCCast-Department-Vote-Logs";

    }


    doc.save(
        `${filename}-${dateString}.pdf`
    );

}


/* =========================================================
   HTML SAFETY
========================================================= */

function escapeHTML(value) {

    return String(value)

        .replaceAll("&", "&amp;")

        .replaceAll("<", "&lt;")

        .replaceAll(">", "&gt;")

        .replaceAll('"', "&quot;")

        .replaceAll("'", "&#039;");

}


function escapeAttribute(value) {

    return escapeHTML(value);

}