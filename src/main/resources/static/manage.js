"use strict";

// Same-origin requests: open http://localhost:8080/manage.html.
function showMessage(id, text, type = "") {
  const element = document.getElementById(id);
  element.textContent = text;
  element.className = type ? "message " + type : "hint";
  element.hidden = false;
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "The server could not process the request. Please try again.");
    if (body === null) throw new Error("The server returned an invalid response.");
    return body;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("Request timed out. Refresh the list before submitting again.");
    if (error instanceof TypeError) throw new Error("Server unavailable. Check that the backend is running at http://localhost:8080.");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function loadTable(url, bodyId, messageId, columns, label) {
  showMessage(messageId, "Loading " + label + "...");
  try {
    const items = await request(url);
    if (!Array.isArray(items)) throw new Error("The server returned an invalid list.");
    const rows = document.createDocumentFragment();
    for (const item of items) {
      const row = document.createElement("tr");
      for (const column of columns) {
        const cell = document.createElement("td");
        cell.textContent = item[column] ?? "Not set";
        row.appendChild(cell);
      }
      if (bodyId === "trainings-body") {
        const cell = document.createElement("td");
        const form = document.createElement("form");
        form.className = "capacity-form";
        const input = document.createElement("input");
        input.type = "number";
        input.min = "1";
        input.max = "2147483647";
        input.step = "1";
        input.required = true;
        input.value = item.maxParticipants ?? "";
        input.setAttribute("aria-label", "Maximum participants for " + item.title);
        const button = document.createElement("button");
        button.type = "submit";
        button.textContent = "Save";
        form.append(input, button);
        form.addEventListener("submit", event => {
          event.preventDefault();
          updateCapacity(item.trainingId, input, button);
        });
        cell.appendChild(form);
        row.appendChild(cell);
      }
      rows.appendChild(row);
    }
    document.getElementById(bodyId).replaceChildren(rows);
    showMessage(messageId, items.length ? items.length + " " + label : "No " + label + " yet.");
  } catch (error) {
    showMessage(messageId, error.message + " Refresh the page to reload the list.", "error");
  }
}

function loadDepartments() {
  return loadTable("/api/departments", "departments-body", "departments-list-message",
    ["departmentId", "code", "name"], "departments");
}

function loadTrainings() {
  return loadTable("/api/trainings", "trainings-body", "trainings-list-message",
    ["trainingId", "title", "trainingDate", "venue", "maxParticipants"], "training programmes");
}

async function createRecord(form, buttonId, messageId, url, payload, successMessage, reload) {
  const button = document.getElementById(buttonId);
  if (button.disabled || !form.reportValidity()) return;
  if (Object.values(payload).some(value => typeof value === "string" && !value.trim())) {
    showMessage(messageId, "Complete all fields. Text fields cannot contain only spaces.", "error");
    return;
  }
  const label = button.textContent;
  button.disabled = true;
  button.textContent = "Saving...";
  document.getElementById(messageId).hidden = true;
  try {
    await request(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    form.reset();
    showMessage(messageId, successMessage, "success");
    await reload();
  } catch (error) {
    showMessage(messageId, error.message, "error");
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
}

function createDepartment(event) {
  event.preventDefault();
  return createRecord(event.currentTarget, "department-submit", "department-message", "/api/departments", {
    name: document.getElementById("department-name").value.trim(),
    code: document.getElementById("department-code").value.trim()
  }, "Department created successfully.", loadDepartments);
}

function createTraining(event) {
  event.preventDefault();
  const maxParticipants = Number(document.getElementById("training-capacity").value);
  if (!Number.isInteger(maxParticipants) || maxParticipants < 1 || maxParticipants > 2147483647) {
    showMessage("training-message", "Maximum Participants must be a positive whole number.", "error");
    return;
  }
  return createRecord(event.currentTarget, "training-submit", "training-message", "/api/trainings", {
    title: document.getElementById("training-title-input").value.trim(),
    trainingDate: document.getElementById("training-date").value,
    venue: document.getElementById("training-venue").value.trim(),
    maxParticipants
  }, "Training programme created successfully.", loadTrainings);
}

async function updateCapacity(trainingId, input, button) {
  if (button.disabled || !input.reportValidity()) return;
  const maxParticipants = Number(input.value);
  if (!Number.isInteger(maxParticipants) || maxParticipants < 1 || maxParticipants > 2147483647) return;
  button.disabled = true;
  button.textContent = "Saving...";
  try {
    await request("/api/trainings/" + trainingId + "/capacity", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ maxParticipants })
    });
    showMessage("training-message", "Capacity updated successfully. Available seats were offered to waiting officers in order.", "success");
    await loadTrainings();
  } catch (error) {
    showMessage("training-message", error.message, "error");
  } finally {
    button.disabled = false;
    button.textContent = "Save";
  }
}

document.getElementById("department-form").addEventListener("submit", createDepartment);
document.getElementById("training-form").addEventListener("submit", createTraining);
loadDepartments();
loadTrainings();
