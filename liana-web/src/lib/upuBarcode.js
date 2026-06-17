const S10_PATTERN = /^[A-Z]{2}\d{9}[A-Z]{2}$/;
const WEIGHT_FACTORS = [8, 6, 4, 2, 3, 5, 9, 7];
const MODULO = 11;

export function normalizeUpuBarcode(value) {
  return String(value ?? '').trim().toUpperCase();
}

export function isUpuBarcode(value) {
  return getUpuBarcodeError(value) === '';
}

export function getUpuBarcodeError(value, label = '运单号') {
  const normalized = normalizeUpuBarcode(value);
  if (!normalized) {
    return `请输入${label}`;
  }
  if (!S10_PATTERN.test(normalized)) {
    return `${label}必须是 UPU S10 格式，例如 AA123456789CN`;
  }

  const serialNumber = normalized.slice(2, 10);
  const checksum = normalized.charAt(10);
  if (checksum !== calculateChecksum(serialNumber)) {
    return `${label}校验位不正确`;
  }

  return '';
}

function calculateChecksum(serialNumber) {
  if (!/^\d{8}$/.test(serialNumber)) {
    throw new Error('serialNumber must be 8 digits');
  }

  let sum = 0;
  for (let i = 0; i < 8; i++) {
    sum += Number(serialNumber.charAt(i)) * WEIGHT_FACTORS[i];
  }

  const remainder = sum % MODULO;
  let checksum = MODULO - remainder;
  if (checksum === MODULO) {
    checksum = 5;
  } else if (checksum === 10) {
    checksum = 0;
  }
  return String(checksum);
}
