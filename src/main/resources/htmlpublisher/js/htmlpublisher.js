function updateBody(tabId) {
  document.getElementById(selectedTab).setAttribute("class", "unselected");
  tab = document.getElementById(tabId)
  tab.setAttribute("class", "selected");
  selectedTab = tabId;
  iframe = document.getElementById("myframe");
  iframe.src = encodeURIComponent(tab.getAttribute("value")).replace(/%2F/g, '/');
}
function init(tabId){
  updateBody(tabId);
}

var selectedTab = "tab1";

window.addEventListener("DOMContentLoaded", () => {
  init("tab1");
  
  const dataHolder = document.querySelector(".links-data-holder");
  const { backToName, rootUrl, jobUrl, zipLink } = dataHolder.dataset;
  const backButton = document.querySelector("#hudson_link");
  backButton.innerText = `Back to ${backToName}`;
  backButton.href = `${rootUrl}/${jobUrl}`;

  document.querySelector("#zip_link").href = `*zip*/${zipLink}.zip`;
  
  document.querySelectorAll("#tabnav li").forEach((item) => {
    item.addEventListener("click", (event) => {
      updateBody(event.target.id);
    });
  });
});
