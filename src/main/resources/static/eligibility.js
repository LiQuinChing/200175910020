"use strict";

const trainingSelect =
  document.getElementById("eligibility-training");

const ruleFormCard =
  document.getElementById("rule-form-card");

const rulesCard =
  document.getElementById("rules-card");

const ruleForm =
  document.getElementById("eligibility-rule-form");

const ruleType =
  document.getElementById("rule-type");

const ruleOperator =
  document.getElementById("rule-operator");

const ruleValue =
  document.getElementById("rule-value");

const normalValueContainer =
  document.getElementById("normal-value-container");

const departmentValueContainer =
  document.getElementById("department-value-container");

const departmentRuleValue =
  document.getElementById("department-rule-value");

const activeCheckbox =
  document.getElementById("rule-active");

const ruleMessage =
  document.getElementById("rule-form-message");

const rulesMessage =
  document.getElementById("rules-message");

const rulesBody =
  document.getElementById("eligibility-rules-body");

const trainingMessage =
  document.getElementById("training-rule-message");


let departments = [];



async function request(url, options = {}) {

  const response = await fetch(url, options);

  return response;
}



function showMessage(element, message, type) {

  element.textContent = message;

  element.className =
    type
      ? "message " + type
      : "hint";

  element.hidden = false;
}



async function loadTrainingProgrammes() {

  try {

    const response =
      await request("/api/trainings");

    if (!response.ok) {
      throw new Error(
        "Unable to load training programmes."
      );
    }

    const trainings =
      await response.json();

    trainingSelect.replaceChildren(
      new Option(
        "Select a training programme",
        ""
      )
    );

    for (const training of trainings) {

      const label =
        training.title +
        (
          training.trainingDate
            ? " - " + training.trainingDate
            : ""
        );

      trainingSelect.add(
        new Option(
          label,
          training.trainingId
        )
      );
    }

    trainingSelect.disabled =
      trainings.length === 0;

    if (!trainings.length) {

      showMessage(
        trainingMessage,
        "No training programmes are available."
      );

    }

  } catch (error) {

    trainingSelect.replaceChildren(
      new Option(
        "Unable to load training programmes",
        ""
      )
    );

    showMessage(
      trainingMessage,
      error.message,
      "error"
    );
  }
}



async function loadDepartments() {

  try {

    const response =
      await request("/api/departments");

    if (!response.ok) {
      throw new Error(
        "Unable to load departments."
      );
    }

    departments =
      await response.json();

    departmentRuleValue.replaceChildren(
      new Option(
        "Select department",
        ""
      )
    );

    for (const department of departments) {

      departmentRuleValue.add(
        new Option(
          department.name,
          department.departmentId
        )
      );
    }

  } catch (error) {

    console.error(error);
  }
}



function updateOperator() {

  const type =
    ruleType.value;

  ruleOperator.replaceChildren();


  if (!type) {

    ruleOperator.add(
      new Option(
        "Select rule type first",
        ""
      )
    );

    ruleOperator.disabled = true;

    return;
  }


  ruleOperator.disabled = false;


  if (type === "DEPARTMENT") {

    ruleOperator.add(
      new Option(
        "IN",
        "IN"
      )
    );

    normalValueContainer.hidden = true;

    departmentValueContainer.hidden = false;

    ruleValue.required = false;

    departmentRuleValue.required = true;

  } else {

    normalValueContainer.hidden = false;

    departmentValueContainer.hidden = true;

    ruleValue.required = true;

    departmentRuleValue.required = false;


    if (
      type === "MIN_YEARS_OF_SERVICE"
    ) {

      ruleOperator.add(
        new Option(">=", ">=")
      );

      ruleValue.type = "number";
      ruleValue.min = "0";
      ruleValue.step = "1";
      ruleValue.placeholder =
        "e.g. 5";

    } else if (
      type ===
      "NO_SAME_TRAINING_WITHIN_MONTHS"
    ) {

      ruleOperator.add(
        new Option("=", "=")
      );

      ruleValue.type = "number";
      ruleValue.min = "1";
      ruleValue.step = "1";
      ruleValue.placeholder =
        "e.g. 12";

    } else {

      ruleOperator.add(
        new Option("=", "=")
      );

      ruleOperator.add(
        new Option("IN", "IN")
      );

      ruleValue.type = "text";
      ruleValue.removeAttribute("min");
      ruleValue.removeAttribute("step");

      ruleValue.placeholder =
        type === "DESIGNATION"
          ? "e.g. Manager"
          : "e.g. Grade I";
    }
  }
}



function departmentName(value) {

  const department =
    departments.find(
      item =>
        String(item.departmentId) ===
        String(value)
    );

  return department
    ? department.name
    : value;
}



function displayRuleValue(rule) {

  if (
    rule.ruleType === "DEPARTMENT"
  ) {

    return rule.ruleValue
      .split(",")
      .map(value =>
        departmentName(value.trim())
      )
      .join(", ");
  }

  return rule.ruleValue;
}



async function loadEligibilityRules(
  trainingId
) {

  rulesBody.replaceChildren();

  showMessage(
    rulesMessage,
    "Loading eligibility rules..."
  );


  try {

    const response =
      await request(
        "/api/trainings/" +
        encodeURIComponent(trainingId) +
        "/eligibility-rules"
      );

    const data =
      await response.json()
        .catch(() => []);

    if (!response.ok) {

      throw new Error(
        data.message ||
        "Unable to load eligibility rules."
      );
    }


    for (const rule of data) {

      const row =
        document.createElement("tr");


      const values = [

        rule.ruleId,

        rule.ruleType,

        rule.operator,

        displayRuleValue(rule),

        rule.isActive
          ? "Yes"
          : "No"

      ];


      for (const value of values) {

        const cell =
          document.createElement("td");

        cell.textContent =
          value ?? "—";

        row.appendChild(cell);
      }


      const actionCell =
        document.createElement("td");


      if (rule.isActive) {

        const button =
          document.createElement("button");

        button.textContent =
          "Deactivate";

        button.className =
          "small-danger-button";

        button.addEventListener(
          "click",
          () =>
            deactivateRule(
              rule.ruleId
            )
        );

        actionCell.appendChild(
          button
        );

      } else {

        actionCell.textContent =
          "Inactive";
      }


      row.appendChild(
        actionCell
      );

      rulesBody.appendChild(
        row
      );
    }


    showMessage(
      rulesMessage,
      data.length
        ? data.length +
          " eligibility rule(s)"
        : "No eligibility rules are configured. Officers are eligible by default."
    );


  } catch (error) {

    showMessage(
      rulesMessage,
      error.message,
      "error"
    );
  }
}



async function createEligibilityRule(
  event
) {

  event.preventDefault();


  const trainingId =
    Number(trainingSelect.value);

  if (!trainingId) {

    showMessage(
      ruleMessage,
      "Select a training programme first.",
      "error"
    );

    return;
  }


  let value;


  if (
    ruleType.value === "DEPARTMENT"
  ) {

    value =
      departmentRuleValue.value;

  } else {

    value =
      ruleValue.value.trim();
  }


  if (
    !ruleType.value ||
    !ruleOperator.value ||
    !value
  ) {

    showMessage(
      ruleMessage,
      "Complete all rule fields.",
      "error"
    );

    return;
  }


  const payload = {

    ruleType:
      ruleType.value,

    operator:
      ruleOperator.value,

    ruleValue:
      value,

    isActive:
      activeCheckbox.checked
  };


  try {

    const response =
      await request(
        "/api/trainings/" +
        trainingId +
        "/eligibility-rules",
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
      await response.json()
        .catch(() => ({}));


    if (!response.ok) {

      throw new Error(
        data.message ||
        "Could not create eligibility rule."
      );
    }


    showMessage(
      ruleMessage,
      "Eligibility rule added successfully.",
      "success"
    );


    ruleForm.reset();

    activeCheckbox.checked =
      true;

    updateOperator();


    await loadEligibilityRules(
      trainingId
    );


  } catch (error) {

    showMessage(
      ruleMessage,
      error.message,
      "error"
    );
  }
}



async function deactivateRule(
  ruleId
) {

  if (
    !confirm(
      "Deactivate this eligibility rule?"
    )
  ) {
    return;
  }


  try {

    const response =
      await request(
        "/api/eligibility-rules/" +
        ruleId,
        {
          method: "DELETE"
        }
      );


    if (!response.ok) {

      const data =
        await response.json()
          .catch(() => ({}));

      throw new Error(
        data.message ||
        "Unable to deactivate rule."
      );
    }


    showMessage(
      ruleMessage,
      "Eligibility rule deactivated.",
      "success"
    );


    await loadEligibilityRules(
      Number(trainingSelect.value)
    );


  } catch (error) {

    showMessage(
      ruleMessage,
      error.message,
      "error"
    );
  }
}



trainingSelect.addEventListener(
  "change",
  async () => {

    const trainingId =
      Number(trainingSelect.value);


    if (!trainingId) {

      ruleFormCard.hidden = true;

      rulesCard.hidden = true;

      showMessage(
        trainingMessage,
        "Select a training programme to configure eligibility rules."
      );

      return;
    }


    trainingMessage.hidden =
      true;

    ruleFormCard.hidden =
      false;

    rulesCard.hidden =
      false;


    await loadEligibilityRules(
      trainingId
    );
  }
);



ruleType.addEventListener(
  "change",
  updateOperator
);


ruleForm.addEventListener(
  "submit",
  createEligibilityRule
);



loadTrainingProgrammes();

loadDepartments();

updateOperator();