"use strict";

// Serve this page from Spring Boot at http://localhost:8080/.
const API_URL = "/api/nominations";
const form = document.getElementById("nomination-form");
const submitButton = document.getElementById("submit-button");
const formMessage = document.getElementById("form-message");
const listMessage = document.getElementById("list-message");
const tableBody = document.getElementById("nominations-body");
let submitting = false;
let capacityVersion = 0;

async function loadSelectedCapacity() {
  const version = ++capacityVersion;
  const trainingId = document.getElementById("trainingId").value;
  const message = document.getElementById("nomination-capacity");
  message.hidden = !trainingId;
  if (!trainingId) return;
  showMessage(message, "Loading capacity...");
  try {
    const response = await request("/api/trainings/" + encodeURIComponent(trainingId) + "/capacity-summary");
    const summary = await response.json().catch(() => ({}));
    if (version !== capacityVersion) return;
    if (!response.ok) throw new Error(summary.message || "Capacity information is unavailable.");
    showMessage(message, "Capacity: " + summary.maxParticipants + " | Confirmed: " + summary.confirmedCount +
      " | Available Seats: " + summary.availableSeats + " | Waiting List: " + summary.waitingListCount +
      ". Full programmes still accept waiting-list nominations.");
  } catch (error) {
    if (version === capacityVersion) showMessage(message, "Capacity information is unavailable. You can still submit a nomination.", "error");
  }
}

function updateSubmitButton() {
  submitButton.disabled = submitting ||
    document.getElementById("trainingId").disabled ||
    document.getElementById("departmentId").disabled;
}

async function loadOptions(url, selectId, messageId, label, format, idKey) {
  const select = document.getElementById(selectId);
  const message = document.getElementById(messageId);
  select.disabled = true;
  updateSubmitButton();
  try {
    const response = await request(url);
    if (!response.ok) throw new Error("Could not load " + label + ". Refresh the page to try again.");
    const items = await response.json();
    if (!Array.isArray(items)) throw new Error("Invalid server response for " + label + ".");
    select.replaceChildren(new Option(items.length ? "Select " + label : "No " + label + " available", ""));
    for (const item of items) select.add(new Option(format(item), item[idKey]));
    select.disabled = items.length === 0;
    if (!items.length) showMessage(message, "Create " + label + " on the Manage Departments & Training page first.");
    else message.hidden = true;
  } catch (error) {
    select.replaceChildren(new Option("Unable to load " + label, ""));
    showMessage(message, error instanceof TypeError || error.name === "AbortError"
      ? "Server unavailable. Check http://localhost:8080 and refresh the page."
      : error.message, "error");
  } finally {
    updateSubmitButton();
  }
}

function loadDepartments() {
  return loadOptions("/api/departments", "departmentId", "department-message", "departments",
    department => department.name, "departmentId");
}

function loadTrainings() {
  return loadOptions("/api/trainings", "trainingId", "training-message", "training programmes",
    training => training.title + (training.trainingDate ? " - " + training.trainingDate : " - Date not set"),
    "trainingId");
}

function showMessage(element, message, type) {
  element.textContent = message;
  element.className = type ? "message " + type : "hint";
  element.hidden = false;
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

function networkMessage(error, submitting = false) {
  if (error.name === "AbortError") {
    return submitting
      ? "The request timed out. Check the nomination list before submitting again."
      : "The request timed out. Please refresh the page to try again.";
  }
  return submitting
    ? "Unable to reach the server. Check that the backend is running at http://localhost:8080, then refresh the list before retrying."
    : "Unable to load nominations. Check that the backend is running at http://localhost:8080 and refresh the page.";
}

async function loadNominations() {
  showMessage(listMessage, "Loading nominations…");
  try {
    // Fetch all pages from the backend's bounded list API.
    const nominations = [];
    const limit = 200;
    let page;
    do {
      const response = await request(API_URL + "?limit=" + limit + "&offset=" + nominations.length);
      if (!response.ok) {
        showMessage(listMessage, "Could not load nominations. Please refresh the page to try again.", "error");
        return;
      }
      page = await response.json();
      if (!Array.isArray(page)) throw new Error("Invalid nomination response");
      nominations.push(...page);
    } while (page.length === limit);

    const rows = document.createDocumentFragment();
    for (const nomination of nominations) {
      const row = document.createElement("tr");
      for (const value of [
        nomination.officerId,
        nomination.officerName,
        nomination.trainingName,
        nomination.departmentName,
        nomination.status
      ]) {
        const cell = document.createElement("td");
        cell.textContent = value ?? "—";
        row.appendChild(cell);
      }
      rows.appendChild(row);
    }
    tableBody.replaceChildren(rows);
    showMessage(listMessage, nominations.length
      ? nominations.length + " nomination(s)"
      : "No nominations yet. Submit the form above to add one.");
  } catch (error) {
    showMessage(listMessage, networkMessage(error), "error");
  }
}

async function submitNomination(event) {
  event.preventDefault();
  if (submitButton.disabled) return;
  if (!form.reportValidity()) return;

  const payload = {
    trainingId: Number(document.getElementById("trainingId").value),
    officerId: Number(document.getElementById("officer-id").value),
    nominatedByDepartmentId: Number(document.getElementById("departmentId").value)
  };
  if (!Object.values(payload).every(value => Number.isSafeInteger(value) && value > 0)) {
    showMessage(formMessage, "Select a training programme and department, and enter a valid positive Officer ID.", "error");
    return;
  }

  submitting = true;
  updateSubmitButton();
  submitButton.textContent = "Submitting…";
  formMessage.hidden = true;
  try {
    const response = await request(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (response.status === 409) {
      const error = await response.json().catch(() => ({}));
      showMessage(formMessage, error.code === "DUPLICATE_NOMINATION"
        ? "Officer is already nominated for this training programme."
        : error.message || "The nomination could not be accepted.", "error");
      await loadNominations();
    } else if (response.ok) {
      const nomination = await response.json().catch(() => ({}));
      if (nomination.status === "CONFIRMED") {
        showMessage(formMessage, "Nomination submitted successfully. Status: CONFIRMED", "success");
      } else if (nomination.status === "WAITING_LIST") {
        showMessage(formMessage, "Nomination submitted successfully. The programme is currently full and the officer has been added to the WAITING LIST.", "success");
      } else {
        showMessage(formMessage, "Nomination submitted successfully. Check the nominations table for its assigned status.", "success");
      }
      await Promise.all([loadNominations(), loadSelectedCapacity()]);
    } else if (response.status === 400 || response.status === 404) {
      const error = await response.json().catch(() => ({}));
      const reasons = Array.isArray(error.reasons) ? " " + error.reasons.join(" ") : "";
      showMessage(formMessage, (error.message || "Invalid input. Check the selected programme, Officer ID and department.") + reasons, "error");
    } else {
      showMessage(formMessage, "The server could not process the nomination. Please try again later.", "error");
    }
  } catch (error) {
    showMessage(formMessage, networkMessage(error, true), "error");
  } finally {
    submitting = false;
    updateSubmitButton();
    submitButton.textContent = "Submit Nomination";
  }
}

form.addEventListener("submit", submitNomination);
document.getElementById("trainingId").addEventListener("change", loadSelectedCapacity);
loadDepartments();
loadTrainings();
loadNominations();
