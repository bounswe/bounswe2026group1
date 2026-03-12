/**
 * BUTTON CLICK HANDLERS
 *
 * Each button on the homepage has its own handler function below.
 * Replace the placeholder console.log with your own implementation.
 */

function onButton1Click() {
  fetch("https://valorant-api.com/v1/agents")
    .then(res => res.json())
    .then(data => {
      const newWindow = window.open("", "_blank");
      newWindow.document.write("<h1>Valorant Agents</h1>");
      newWindow.document.write("<p>This page displays all playable agents in Valorant.</p>");
      data.data.forEach(agent => {
        newWindow.document.write(`<h2>${agent.displayName}</h2>`);
        newWindow.document.write(`<p>${agent.description}</p>`);
        newWindow.document.write(`<img src="${agent.displayIcon}" width="100"/>`);
        newWindow.document.write("<hr/>");
      });
    });
}

function onButton2Click() {
  console.log("Button 2 clicked -- implement me!");
}

function onButton3Click() {
  window.location.href = 'mertozustun-api.html';
}

function onButton4Click() {
  console.log("Button 4 clicked -- implement me!");
}

function onButton5Click() {
  console.log("Button 5 clicked -- implement me!");
}

function onButton6Click() {
  console.log("Button 6 clicked -- implement me!");
}


function onButton7Click() {
  window.location.href = "mustafa.html";
}

function onButton8Click() {
  window.location.href = "burak-api.html";
}
}
