"use strict";

// ============================================================
// Government Training Management System
// Submit Nomination Page
//
// Task 1:
//   - Duplicate nomination prevention
//
// Task 2:
//   - Capacity handling
//   - CONFIRMED / WAITING_LIST
//
// Task 3:
//   - Training eligibility check
// ============================================================


// ------------------------------------------------------------
// API
// ------------------------------------------------------------

const API_URL = "/api/nominations";


// ------------------------------------------------------------
// DOM ELEMENTS
// ------------------------------------------------------------

const form = document.getElementById("nomination-form");

const submitButton =
  document.getElementById("submit-button");

const formMessage =
  document.getElementById("form-message");

const listMessage =
  document.getElementById("list-message");

const tableBody =
  document.getElementById("nominations-body");

const trainingSelect =
  document.getElementById("trainingId");

const departmentSelect =
  document.getElementById("departmentId");

const officerIdInput =
  document.getElementById("officer-id");

const eligibilityButton =
  document.getElementById("check-eligibility-button");

const eligibilityMessage =
  document.getElementById("eligibility-message");

const capacityMessage =
  document.getElementById("nomination-capacity");


// ------------------------------------------------------------
// PAGE STATE
// ------------------------------------------------------------

let submitting = false;

let capacityVersion = 0;

let checkingEligibility = false;


// ============================================================
// COMMON FUNCTIONS
// ============================================================

function showMessage(element, message, type) {

  if (!element) {
    return;
  }

  element.textContent = message;

  element.className =
    type
      ? "message " + type
      : "hint";

  element.hidden = false;
}


function hideMessage(element) {

  if (element) {
    element.hidden = true;
  }
}


async function request(url, options = {}) {

  const controller =
    new AbortController();

  const timeout =
    setTimeout(
      () => controller.abort(),
      10000
    );

  try {

    return await fetch(
      url,
      {
        ...options,
        signal: controller.signal
      }
    );

  } finally {

    clearTimeout(timeout);
  }
}


function networkMessage(
  error,
  submittingRequest = false
) {

  if (error.name === "AbortError") {

    return submittingRequest
      ? "The request timed out. Check the nomination list before submitting again."
      : "The request timed out. Please refresh the page and try again.";
  }

  return submittingRequest
    ? "Unable to reach the server. Check that the Spring Boot backend is running."
    : "Unable to reach the server. Check that the Spring Boot backend is running at http://localhost:8080.";
}


// ============================================================
// SUBMIT BUTTON STATE
// ============================================================

function updateSubmitButton() {

  if (!submitButton) {
    return;
  }

  submitButton.disabled =
    submitting ||
    trainingSelect.disabled ||
    departmentSelect.disabled;
}


// ============================================================
// LOAD DEPARTMENTS / TRAINING PROGRAMMES
// ============================================================

async function loadOptions(
  url,
  selectId,
  messageId,
  label,
  format,
  idKey
) {

  const select =
    document.getElementById(selectId);

  const message =
    document.getElementById(messageId);

  select.disabled = true;

  updateSubmitButton();

  try {

    const response =
      await request(url);


    if (!response.ok) {

      throw new Error(
        "Could not load " +
        label +
        ". Refresh the page to try again."
      );
    }


    const items =
      await response.json();


    if (!Array.isArray(items)) {

      throw new Error(
        "Invalid server response for " +
        label +
        "."
      );
    }


    select.replaceChildren(

      new Option(
        items.length
          ? "Select " + label
          : "No " + label + " available",
        ""
      )
    );


    for (const item of items) {

      select.add(

        new Option(
          format(item),
          item[idKey]
        )
      );
    }


    select.disabled =
      items.length === 0;


    if (!items.length) {

      showMessage(
        message,
        "Create " +
        label +
        " on the Manage Departments & Training page first."
      );

    } else {

      hideMessage(message);
    }


  } catch (error) {

    select.replaceChildren(

      new Option(
        "Unable to load " + label,
        ""
      )
    );


    const messageText =
      error instanceof TypeError ||
      error.name === "AbortError"

        ? "Server unavailable. Check http://localhost:8080 and refresh the page."

        : error.message;


    showMessage(
      message,
      messageText,
      "error"
    );


  } finally {

    updateSubmitButton();
  }
}


function loadDepartments() {

  return loadOptions(

    "/api/departments",

    "departmentId",

    "department-message",

    "departments",

    department =>
      department.name,

    "departmentId"
  );
}


function loadTrainings() {

  return loadOptions(

    "/api/trainings",

    "trainingId",

    "training-message",

    "training programmes",

    training =>
      training.title +
      (
        training.trainingDate
          ? " - " + training.trainingDate
          : " - Date not set"
      ),

    "trainingId"
  );
}


// ============================================================
// TASK 2 - CAPACITY INFORMATION
// ============================================================

async function loadSelectedCapacity() {

  const version =
    ++capacityVersion;

  const trainingId =
    trainingSelect.value;


  capacityMessage.hidden =
    !trainingId;


  if (!trainingId) {
    return;
  }


  showMessage(
    capacityMessage,
    "Loading capacity..."
  );


  try {

    const response =
      await request(

        "/api/trainings/" +
        encodeURIComponent(trainingId) +
        "/capacity-summary"
      );


    const summary =
      await response
        .json()
        .catch(() => ({}));


    if (version !== capacityVersion) {
      return;
    }


    if (!response.ok) {

      throw new Error(

        summary.message ||
        "Capacity information is unavailable."
      );
    }


    showMessage(

      capacityMessage,

      "Capacity: " +
      summary.maxParticipants +

      " | Confirmed: " +
      summary.confirmedCount +

      " | Available Seats: " +
      summary.availableSeats +

      " | Waiting List: " +
      summary.waitingListCount +

      ". Full programmes still accept waiting-list nominations."
    );


  } catch (error) {

    if (version === capacityVersion) {

      showMessage(

        capacityMessage,

        "Capacity information is unavailable. You can still submit a nomination.",

        "error"
      );
    }
  }
}


// ============================================================
// TASK 3 - ELIGIBILITY CHECK
// ============================================================

function clearEligibilityResult() {

  hideMessage(
    eligibilityMessage
  );
}


async function checkOfficerEligibility() {

  const trainingId =
    Number(
      trainingSelect.value
    );

  const officerId =
    Number(
      officerIdInput.value
    );


  if (
    !Number.isSafeInteger(trainingId) ||
    trainingId <= 0
  ) {

    showMessage(

      eligibilityMessage,

      "Please select a training programme first.",

      "error"
    );

    return;
  }


  if (
    !Number.isSafeInteger(officerId) ||
    officerId <= 0
  ) {

    showMessage(

      eligibilityMessage,

      "Please enter a valid Officer ID first.",

      "error"
    );

    return;
  }


  if (checkingEligibility) {
    return;
  }


  checkingEligibility = true;

  eligibilityButton.disabled =
    true;

  eligibilityButton.textContent =
    "Checking...";


  try {

    const response =
      await request(

        "/api/trainings/" +
        encodeURIComponent(trainingId) +
        "/eligibility/" +
        encodeURIComponent(officerId)
      );


    const data =
      await response
        .json()
        .catch(() => ({}));


    if (!response.ok) {

      throw new Error(

        data.message ||
        "Unable to check officer eligibility."
      );
    }


    if (data.eligible === true) {

      showMessage(

        eligibilityMessage,

        "Officer is eligible for this training programme.",

        "success"
      );

      return;
    }


    const reasons =
      Array.isArray(data.reasons)
        ? data.reasons
        : [];


    let message =
      "Officer is NOT eligible for this training programme.";


    if (reasons.length > 0) {

      message +=
        " Reasons: " +
        reasons.join(" ");
    }


    showMessage(

      eligibilityMessage,

      message,

      "error"
    );


  } catch (error) {

    const message =

      error.name === "AbortError"

        ? "Eligibility check timed out. Please try again."

        : error instanceof TypeError

          ? "Unable to reach the server. Check that the backend is running."

          : error.message;


    showMessage(

      eligibilityMessage,

      message,

      "error"
    );


  } finally {

    checkingEligibility = false;

    eligibilityButton.disabled =
      false;

    eligibilityButton.textContent =
      "Check Eligibility";
  }
}


// ============================================================
// LOAD EXISTING NOMINATIONS
// ============================================================

async function loadNominations() {

  showMessage(
    listMessage,
    "Loading nominations..."
  );


  try {

    const nominations = [];

    const limit = 200;

    let page;


    do {

      const response =
        await request(

          API_URL +
          "?limit=" +
          limit +
          "&offset=" +
          nominations.length
        );


      if (!response.ok) {

        showMessage(

          listMessage,

          "Could not load nominations. Please refresh the page to try again.",

          "error"
        );

        return;
      }


      page =
        await response.json();


      if (!Array.isArray(page)) {

        throw new Error(
          "Invalid nomination response."
        );
      }


      nominations.push(...page);


    } while (
      page.length === limit
    );


    const rows =
      document.createDocumentFragment();


    for (const nomination of nominations) {

      const row =
        document.createElement("tr");


      const values = [

        nomination.officerId,

        nomination.officerName,

        nomination.trainingName,

        nomination.departmentName,

        nomination.status
      ];


      for (const value of values) {

        const cell =
          document.createElement("td");

        cell.textContent =
          value ?? "—";

        row.appendChild(cell);
      }


      rows.appendChild(row);
    }


    tableBody.replaceChildren(
      rows
    );


    showMessage(

      listMessage,

      nominations.length

        ? nominations.length +
          " nomination(s)"

        : "No nominations yet. Submit the form above to add one."
    );


  } catch (error) {

    showMessage(

      listMessage,

      networkMessage(error),

      "error"
    );
  }
}


// ============================================================
// SUBMIT NOMINATION
// ============================================================

async function submitNomination(event) {

  event.preventDefault();


  if (
    submitButton.disabled
  ) {
    return;
  }


  if (
    !form.reportValidity()
  ) {
    return;
  }


  const payload = {

    trainingId:
      Number(
        trainingSelect.value
      ),

    officerId:
      Number(
        officerIdInput.value
      ),

    nominatedByDepartmentId:
      Number(
        departmentSelect.value
      )
  };


  if (
    !Object.values(payload)
      .every(
        value =>
          Number.isSafeInteger(value) &&
          value > 0
      )
  ) {

    showMessage(

      formMessage,

      "Select a training programme and department, and enter a valid positive Officer ID.",

      "error"
    );

    return;
  }


  submitting = true;

  updateSubmitButton();

  submitButton.textContent =
    "Submitting...";

  hideMessage(
    formMessage
  );


  try {

    const response =
      await request(

        API_URL,

        {
          method: "POST",

          headers: {
            "Content-Type":
              "application/json"
          },

          body:
            JSON.stringify(payload)
        }
      );


    const data =
      await response
        .json()
        .catch(() => ({}));


    // --------------------------------------------------------
    // Task 1 - Duplicate nomination
    // --------------------------------------------------------

    if (
      response.status === 409 &&
      data.code === "DUPLICATE_NOMINATION"
    ) {

      showMessage(

        formMessage,

        "Officer is already nominated for this training programme.",

        "error"
      );


      await loadNominations();

      return;
    }


    // --------------------------------------------------------
    // Successful nomination
    // Task 2 result:
    // CONFIRMED or WAITING_LIST
    // --------------------------------------------------------

    if (response.ok) {

      if (
        data.status === "CONFIRMED"
      ) {

        showMessage(

          formMessage,

          "Nomination submitted successfully. Status: CONFIRMED",

          "success"
        );

      } else if (
        data.status === "WAITING_LIST"
      ) {

        showMessage(

          formMessage,

          "Nomination submitted successfully. The programme is currently full and the officer has been added to the WAITING LIST.",

          "success"
        );

      } else {

        showMessage(

          formMessage,

          "Nomination submitted successfully. Check the nominations table for its assigned status.",

          "success"
        );
      }


      await Promise.all([

        loadNominations(),

        loadSelectedCapacity()
      ]);


      return;
    }


    // --------------------------------------------------------
    // Task 3 - Eligibility rejection
    // --------------------------------------------------------

    if (
      data.code === "OFFICER_NOT_ELIGIBLE"
    ) {

      const reasons =
        Array.isArray(data.reasons)
          ? data.reasons
          : [];


      let message =
        data.message ||
        "Officer does not meet the eligibility requirements.";


      if (
        reasons.length > 0
      ) {

        message +=
          " Reasons: " +
          reasons.join(" ");
      }


      showMessage(

        formMessage,

        message,

        "error"
      );


      return;
    }


    // --------------------------------------------------------
    // Other validation errors
    // --------------------------------------------------------

    if (
      response.status === 400 ||
      response.status === 404 ||
      response.status === 409
    ) {

      const reasons =
        Array.isArray(data.reasons)
          ? data.reasons
          : [];


      let message =

        data.message ||

        "The nomination could not be accepted. Check the training programme, Officer ID and department.";


      if (
        reasons.length > 0
      ) {

        message +=
          " Reasons: " +
          reasons.join(" ");
      }


      showMessage(

        formMessage,

        message,

        "error"
      );


      return;
    }


    // --------------------------------------------------------
    // Unexpected server response
    // --------------------------------------------------------

    showMessage(

      formMessage,

      data.message ||
      "The server could not process the nomination. Please try again later.",

      "error"
    );


  } catch (error) {

    showMessage(

      formMessage,

      networkMessage(
        error,
        true
      ),

      "error"
    );


  } finally {

    submitting = false;

    updateSubmitButton();

    submitButton.textContent =
      "Submit Nomination";
  }
}


// ============================================================
// EVENT LISTENERS
// ============================================================

form.addEventListener(
  "submit",
  submitNomination
);


trainingSelect.addEventListener(
  "change",
  () => {

    clearEligibilityResult();

    loadSelectedCapacity();
  }
);


officerIdInput.addEventListener(
  "input",
  clearEligibilityResult
);


eligibilityButton.addEventListener(
  "click",
  checkOfficerEligibility
);


// ============================================================
// INITIAL PAGE LOAD
// ============================================================

loadDepartments();

loadTrainings();

loadNominations();