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
    window.location.href = "country-info.html";
}

function onButton3Click() {
  window.location.href = 'mertozustun-api.html';
}

function onButton4Click() {
  var accession = prompt("Enter a UniProt accession ID (e.g. P05067):");
  if (!accession || !accession.trim()) return;

  // Open the window synchronously so popup blockers allow it
  var win = window.open("", "_blank");
  if (!win) {
    alert("Popup was blocked. Please allow popups for this page and try again.");
    return;
  }

  var url = "https://rest.uniprot.org/uniprotkb/" + accession.trim();

  fetch(url, { headers: { "accept": "application/json" } })
    .then(function (response) {
      if (!response.ok) throw new Error(response.status + ": " + response.statusText);
      return response.json();
    })
    .then(function (data) {
      var proteinName =
        (data.proteinDescription &&
          data.proteinDescription.recommendedName &&
          data.proteinDescription.recommendedName.fullName &&
          data.proteinDescription.recommendedName.fullName.value) ||
        accession;
      var organism = (data.organism && data.organism.scientificName) || "Unknown organism";
      var gene = (data.genes && data.genes[0] && data.genes[0].geneName && data.genes[0].geneName.value) || "N/A";
      var functionEntry = data.comments && data.comments.find(function (c) { return c.commentType === "FUNCTION"; });
      var function_text = (functionEntry && functionEntry.texts && functionEntry.texts[0].value) || "No functional description available.";

      var html =
        "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>" +
        "<title>UniProt: " + proteinName + "</title>" +
        "<style>" +
        "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:800px;margin:48px auto;padding:0 24px;color:#1e293b;}" +
        "h1{font-size:1.6rem;margin-bottom:4px;}" +
        ".meta{color:#64748b;margin-bottom:24px;font-size:0.95rem;}" +
        ".section{background:#f8fafc;border-radius:12px;padding:20px 24px;margin-bottom:20px;}" +
        ".section h2{font-size:1rem;font-weight:600;margin-bottom:8px;color:#4f46e5;}" +
        ".section p{line-height:1.6;margin:0;}" +
        ".info{color:#475569;font-size:0.875rem;border-top:1px solid #e2e8f0;padding-top:16px;margin-top:8px;}" +
        "pre{white-space:pre-wrap;word-break:break-all;font-size:0.78rem;background:#f1f5f9;border-radius:8px;padding:16px;overflow:auto;max-height:400px;}" +
        "</style></head><body>" +
        "<h1>" + proteinName + "</h1>" +
        "<p class='meta'>Accession: <strong>" + accession.trim() + "</strong> &nbsp;|&nbsp; Gene: <strong>" + gene + "</strong> &nbsp;|&nbsp; Organism: <em>" + organism + "</em></p>" +
        "<div class='section'><h2>What is this API?</h2>" +
        "<p>This data comes from the <strong>UniProt REST API</strong> (<code>rest.uniprot.org/uniprotkb</code>). " +
        "UniProt is the world's leading database of protein sequences and functional annotations. " +
        "Each entry describes a single protein: its name, gene, organism, biological function, structure, and more.</p></div>" +
        "<div class='section'><h2>Protein Function</h2><p>" + function_text + "</p></div>" +
        "<div class='section'><h2>Raw JSON Response</h2>" +
        "<p class='info'>Full response from the API for accession <strong>" + accession.trim() + "</strong>:</p>" +
        "<pre>" + JSON.stringify(data, null, 2) + "</pre></div>" +
        "</body></html>";

      // Create a Blob URL and navigate the new window to it
      var blob = new Blob([html], { type: "text/html" });
      var blobUrl = URL.createObjectURL(blob);
      win.location.href = blobUrl;
    })
    .catch(function (err) {
      win.close();
      alert("Failed to fetch UniProt data:\n" + err.message);
    });
}

async function onButton5Click() {
  const response = await fetch('https://dog.ceo/api/breeds/image/random');
  const data = await response.json();
  
  window.location.href = `yeldos.html?img=${encodeURIComponent(data.message)}`;
}

function onButton6Click() {
  window.location.href = "cat.html";
}

function onButton7Click() {
  window.location.href = "mustafa.html";
}

function onButton8Click() {
  window.location.href = "burak-api.html";
}
