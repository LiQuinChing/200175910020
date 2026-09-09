"use strict";

const programmeSelect = document.getElementById("trainingProgramme");
let selectionVersion = 0;
let cancelling = false;

function showMessage(id, message, type = "") {
  const element = document.getElementById(id);
  element.textContent = message;
  element.className = type ? "message " + type : "hint";
  element.hidden = false;
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "The server could not complete the request.");
    if (body === null) throw new Error("The server returned an invalid response.");
    return body;
  } catch (error) {
    if (error.name === "AbortError") {
      throw new Error("The request timed out. Reselect the programme to check its latest status before retrying.");
    }
    if (error instanceof TypeError) {
      throw new Error("Server unavailable. Check that the backend is running at http://localhost:8080.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function loadTrainingProgrammes() {
  try {
    const programmes = await request("/api/trainings");
    if (!Array.isArray(programmes)) throw new Error("The server returned an invalid programme list.");
    programmeSelect.replaceChildren(new Option("Select a training programme", ""));
    for (const programme of programmes) {
      programmeSelect.add(new Option(programme.title + " - " + (programme.trainingDate || "Date not set"), programme.trainingId));
    }
    programmeSelect.disabled = programmes.length === 0;
    if (!programmes.length) {
      showMessage("programme-message", "No training programmes are available. Create one on the management page.");
    }
  } catch (error) {
    programmeSelect.replaceChildren(new Option("Unable to load training programmes", ""));
    showMessage("programme-message", error.message + " Refresh the page to try again.", "error");
  }
}

async function loadCapacitySummary(trainingId, version = selectionVersion) {
  document.getElementById("summary-content").hidden = true;
  showMessage("summary-message", "Loading capacity summary...");
  try {
    const summary = await request("/api/trainings/" + encodeURIComponent(trainingId) + "/capacity-summary");
    if (version !== selectionVersion) return;
    document.getElementById("programme-title").textContent = summary.title;
    for (const [id, key] of [
      ["maximum-capacity", "maxParticipants"], ["confirmed-count", "confirmedCount"],
      ["waiting-count", "waitingListCount"], ["cancelled-count", "cancelledCount"],
      ["available-seats", "availableSeats"]
    ]) document.getElementById(id).textContent = summary[key];
    document.getElementById("summary-content").hidden = false;
    document.getElementById("summary-message").hidden = true;
  } catch (error) {
    if (version === selectionVersion) showMessage("summary-message", error.message, "error");
  }
}

function formatDateTime(value) {
  if (!value) return "Not set";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(undefined, {
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false
  });
}

async function loadProgrammeNominations(trainingId, version = selectionVersion) {
  const tableBody = document.getElementById("participants-body");
  tableBody.replaceChildren();
  showMessage("participants-message", "Loading participants...");
  try {
    const nominations = await request("/api/trainings/" + encodeURIComponent(trainingId) + "/nominations");
    if (version !== selectionVersion) return;
    if (!Array.isArray(nominations)) throw new Error("The server returned an invalid participant list.");
    const rows = document.createDocumentFragment();
    // Preserve the exact ordering supplied by the backend.
    for (const nomination of nominations) {
      const row = document.createElement("tr");
      for (const value of [
        nomination.nominationId, nomination.officerId, nomination.officerName,
        nomination.departmentName, formatDateTime(nomination.nominationDateTime)
      ]) {
        const cell = document.createElement("td");
        cell.textContent = value ?? "Not set";
        row.appendChild(cell);
      }
      const statusCell = document.createElement("td");
      const badge = document.createElement("span");
      const classes = { CONFIRMED: "badge-confirmed", WAITING_LIST: "badge-waiting", CANCELLED: "badge-cancelled" };
      badge.className = "status-badge " + (classes[nomination.status] || "");
      badge.textContent = nomination.status;
      statusCell.appendChild(badge);
      row.appendChild(statusCell);
      const actionCell = document.createElement("td");
      if (nomination.status === "CONFIRMED") {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "cancel-button";
        button.textContent = "Cancel Participation";
        button.disabled = cancelling;
        button.setAttribute("aria-label", "Cancel participation for officer " + nomination.officerId);
        button.addEventListener("click", () => cancelNomination(nomination.nominationId));
        actionCell.appendChild(button);
      }
      row.appendChild(actionCell);
      rows.appendChild(row);
    }
    tableBody.replaceChildren(rows);
    showMessage("participants-message", nominations.length
      ? nominations.length + " nomination(s)"
      : "No nominations have been received for this training programme.");
  } catch (error) {
    if (version === selectionVersion) showMessage("participants-message", error.message, "error");
  }
}

async function loadProgrammeDetails(trainingId) {
  const version = ++selectionVersion;
  const selected = Boolean(trainingId);
  document.getElementById("summary-card").hidden = !selected;
  document.getElementById("participants-card").hidden = !selected;
  document.getElementById("programme-message").hidden = selected;
  if (!selected) {
    showMessage("programme-message", "Please select a training programme to view participants.");
    document.getElementById("participants-body").replaceChildren();
    return;
  }
  await Promise.all([
    loadCapacitySummary(trainingId, version),
    loadProgrammeNominations(trainingId, version)
  ]);
}

async function cancelNomination(nominationId) {
  if (cancelling || !window.confirm("Cancel this participation? The first waiting-list officer may be confirmed automatically.")) return;
  const trainingId = programmeSelect.value;
  cancelling = true;
  programmeSelect.disabled = true;
  document.querySelectorAll(".cancel-button").forEach(button => { button.disabled = true; });
  document.getElementById("action-message").hidden = true;
  try {
    await request("/api/nominations/" + encodeURIComponent(nominationId) + "/cancel", { method: "PUT" });
    showMessage("action-message", "Nomination cancelled successfully.", "success");
  } catch (error) {
    showMessage("action-message", error.message, "error");
  } finally {
    // Refresh after an error too: a timeout or another user may have changed the status.
    await loadProgrammeDetails(trainingId);
    cancelling = false;
    programmeSelect.disabled = false;
    document.querySelectorAll(".cancel-button").forEach(button => { button.disabled = false; });
  }
}

programmeSelect.addEventListener("change", () => {
  document.getElementById("action-message").hidden = true;
  loadProgrammeDetails(programmeSelect.value);
});
loadTrainingProgrammes();

