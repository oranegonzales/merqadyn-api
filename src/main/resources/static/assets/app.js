let overview = null;

const number = new Intl.NumberFormat("en-JM", { maximumFractionDigits: 3 });
const dateTime = new Intl.DateTimeFormat("en-JM", {
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit"
});

function escapeHtml(value) {
  const element = document.createElement("span");
  element.textContent = String(value);
  return element.innerHTML;
}

function formatMoney(value, currency) {
  return new Intl.NumberFormat("en-JM", {
    style: "currency",
    currency,
    maximumFractionDigits: 0
  }).format(value);
}

function renderLocations(locations) {
  const target = document.querySelector("#location-grid");
  target.replaceChildren(...locations.map(location => {
    const card = document.createElement("article");
    card.className = "location-card";
    card.innerHTML = `
      <span class="location-code">${escapeHtml(location.code)}</span>
      <h3>${escapeHtml(location.name)}</h3>
      <p>${escapeHtml(location.address)}</p>
      <div class="location-quantity"><strong>${number.format(location.unitsOnHand)}</strong><span>units / ${number.format(location.itemCount)} lines</span></div>
    `;
    return card;
  }));
}

function renderInventory(locationId = "all") {
  const body = document.querySelector("#inventory-body");
  const rows = overview.inventory.filter(item => locationId === "all" || item.locationId === locationId);
  if (!rows.length) {
    body.innerHTML = '<tr><td colspan="7" class="table-message">No inventory is recorded for this location.</td></tr>';
    return;
  }
  body.replaceChildren(...rows.map(item => {
    const row = document.createElement("tr");
    const lowClass = Number(item.available) <= 5 ? "low-quantity" : "";
    row.innerHTML = `
      <td class="product-cell">${escapeHtml(item.productName)}</td>
      <td class="muted-cell">${escapeHtml(item.sku)}</td>
      <td>${escapeHtml(item.locationName)}</td>
      <td>${number.format(item.onHand)}</td>
      <td>${number.format(item.reserved)}</td>
      <td class="${lowClass}">${number.format(item.available)}</td>
      <td class="muted-cell">v${number.format(item.version)}</td>
    `;
    return row;
  }));
}

function renderActivity(activity) {
  document.querySelector("#activity-count").textContent = `${activity.length} entries`;
  const list = document.querySelector("#activity-list");
  list.replaceChildren(...activity.slice(0, 8).map(entry => {
    const item = document.createElement("article");
    item.className = "activity-item";
    item.innerHTML = `
      <span class="activity-cursor">${String(entry.cursor).padStart(3, "0")}</span>
      <div class="activity-copy"><strong>${escapeHtml(entry.summary)}</strong><span>${escapeHtml(entry.entityType.toLowerCase())} / ${escapeHtml(entry.operation.toLowerCase())}</span></div>
      <time class="activity-time" datetime="${entry.occurredAt}">${dateTime.format(new Date(entry.occurredAt))}</time>
    `;
    return item;
  }));
}

function renderDevices(devices) {
  const list = document.querySelector("#device-list");
  list.replaceChildren(...devices.map(device => {
    const row = document.createElement("article");
    row.className = "device-row";
    row.innerHTML = `
      <strong>${escapeHtml(device.name)}</strong>
      <span>${escapeHtml(device.locationName)}</span>
      <span>${escapeHtml(device.platform.toLowerCase())}</span>
      <span>app ${escapeHtml(device.appVersion)}</span>
      <span>cursor ${number.format(device.lastCursor)}</span>
    `;
    return row;
  }));
}

function populateFilter(locations) {
  const filter = document.querySelector("#location-filter");
  locations.forEach(location => {
    const option = document.createElement("option");
    option.value = location.id;
    option.textContent = location.name;
    filter.append(option);
  });
  filter.addEventListener("change", event => renderInventory(event.target.value));
}

async function loadOverview() {
  try {
    const configResponse = await fetch("/api/v1/config");
    if (!configResponse.ok) throw new Error(`Configuration request failed with ${configResponse.status}`);
    const config = await configResponse.json();
    const merchantId = config.demoMerchantId;
    const response = await fetch(`/api/v1/merchants/${merchantId}/overview`);
    if (!response.ok) throw new Error(`Request failed with ${response.status}`);
    overview = await response.json();
    document.querySelector("#merchant-name").textContent = overview.merchant.name;
    document.querySelector("#latest-cursor").textContent = overview.latestCursor;
    document.querySelector("#last-updated").textContent = dateTime.format(new Date());
    document.querySelector("#units-on-hand").textContent = number.format(overview.unitsOnHand);
    document.querySelector("#inventory-value").textContent = formatMoney(overview.inventoryValue, overview.merchant.currency);
    document.querySelector("#low-stock").textContent = overview.lowStockItems;
    document.querySelector("#device-count").textContent = overview.deviceCount;
    renderLocations(overview.locations);
    renderInventory();
    renderActivity(overview.recentActivity);
    renderDevices(overview.devices);
    populateFilter(overview.locations);
  } catch (error) {
    document.querySelector("#last-updated").textContent = "Data could not be loaded";
    document.querySelector("#inventory-body").innerHTML = '<tr><td colspan="7" class="table-message">The API is unavailable. Confirm that the application and PostgreSQL are running.</td></tr>';
  }
}

loadOverview();
