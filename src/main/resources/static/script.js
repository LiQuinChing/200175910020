"use strict";

// Serve this page from Spring Boot at http://localhost:8080/.
const API_URL = "/api/nominations";
const form = document.getElementById("nomination-form");
const submitButton = document.getElementById("submit-button");
const formMessage = document.getElementById("form-message");
const listMessage = document.getElementById("list-message");
const tableBody = document.getElementById("nominations-body");

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

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (submitButton.disabled) return;
  if (!form.reportValidity()) return;

  const payload = {
    trainingId: Number(document.getElementById("training").value),
    officerId: Number(document.getElementById("officer-id").value),
    departmentId: Number(document.getElementById("department").value)
  };
  if (!Object.values(payload).every(value => Number.isSafeInteger(value) && value > 0)) {
    showMessage(formMessage, "Select a training programme and department, and enter a valid positive Officer ID.", "error");
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Submitting…";
  formMessage.hidden = true;
  try {
    const response = await request(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (response.status === 409) {
      showMessage(formMessage, "This officer is already nominated for this training programme.", "error");
      await loadNominations();
    } else if (response.ok) {
      showMessage(formMessage, "Nomination submitted successfully.", "success");
      await loadNominations();
    } else if (response.status === 400) {
      const error = await response.json().catch(() => ({}));
      showMessage(formMessage, error.message || "Invalid input. Check the selected programme, Officer ID and department.", "error");
    } else {
      showMessage(formMessage, "The server could not process the nomination. Please try again later.", "error");
    }
  } catch (error) {
    showMessage(formMessage, networkMessage(error, true), "error");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Submit Nomination";
  }
});

loadNominations();

