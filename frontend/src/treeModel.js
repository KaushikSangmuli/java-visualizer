// Recognises binary trees in a heap snapshot and lays them out for TreeDiagram.

const CHILD_FIELD = /^(left|right|leftchild|rightchild|lchild|rchild|l|r)$/i;
const PARENT_FIELD = /^(parent|par|up)$/i;
const MAX_NODES = 200;

const isChildField = (field, object) => CHILD_FIELD.test(field.name) && field.type === object.className;
const side = (name) => (/^(left|leftchild|lchild|l)$/i.test(name) ? 'left' : 'right');

/** A user object with a `left`/`right` (or similar) field of its own class is a binary-tree node. */
export function isTreeNodeObject(object) {
  return object?.kind === 'object' && (object.fields ?? []).some((field) => isChildField(field, object));
}

function plain(value) {
  if (!value) return '—';
  if (value.referenceId) return `#${value.referenceId}`;
  return value.value ?? 'null';
}

/** The data shown inside a node: every field except child/parent links (e.g. `val`, or `key: value`). */
function nodeLabel(object) {
  const data = (object.fields ?? []).filter((field) => !isChildField(field, object)
    && !(PARENT_FIELD.test(field.name) && field.type === object.className));
  if (data.length === 0) return `#${object.id}`;
  return data.slice(0, 2).map((field) => plain(field.value)).join(' : ');
}

/** id -> { id, label, leftId, rightId } for every user tree node in the heap. */
export function userTreeNodes(heap) {
  const nodes = new Map();
  for (const object of heap?.objects ?? []) {
    if (!isTreeNodeObject(object)) continue;
    const node = { id: object.id, label: nodeLabel(object), leftId: null, rightId: null, red: false };
    for (const field of object.fields) {
      if (isChildField(field, object) && field.value?.referenceId) node[`${side(field.name)}Id`] = field.value.referenceId;
    }
    nodes.set(object.id, node);
  }
  return nodes;
}

/** Tree nodes not referenced as a child by another node: the roots to draw. */
export function treeRoots(nodes) {
  const children = new Set();
  nodes.forEach((node) => { if (node.leftId) children.add(node.leftId); if (node.rightId) children.add(node.rightId); });
  return [...nodes.keys()].filter((id) => !children.has(id));
}

/** TreeMap/TreeSet nodes from the backend's collection view. */
export function collectionTreeNodes(collection) {
  const nodes = new Map();
  for (const node of collection?.tree ?? []) {
    const key = plain(node.key);
    nodes.set(node.id, {
      id: node.id,
      label: node.value ? `${key} : ${plain(node.value)}` : key,
      leftId: node.leftId,
      rightId: node.rightId,
      red: node.red,
    });
  }
  return nodes;
}

/**
 * Classic tidy layout: x = position in an in-order walk (so nothing overlaps and a BST reads
 * left-to-right in sorted order), y = depth. Cycles and very large trees are cut safely.
 */
export function layoutTree(nodes, rootId) {
  const placed = [];
  const edges = [];
  const seen = new Set();
  let column = 0;
  let maxDepth = 0;
  const visit = (id, depth, parent) => {
    const node = nodes.get(id);
    if (!node || seen.has(id) || seen.size >= MAX_NODES) return null;
    seen.add(id);
    const leftPos = node.leftId ? visit(node.leftId, depth + 1, id) : null;
    const position = { node, x: column, y: depth };
    column += 1;
    maxDepth = Math.max(maxDepth, depth);
    placed.push(position);
    const rightPos = node.rightId ? visit(node.rightId, depth + 1, id) : null;
    if (leftPos) edges.push({ from: position, to: leftPos, side: 'left' });
    if (rightPos) edges.push({ from: position, to: rightPos, side: 'right' });
    return position;
  };
  visit(rootId, 0, null);
  return { placed, edges, columns: column, depth: maxDepth, truncated: seen.size >= MAX_NODES };
}

/** Which variable names point at each heap id, across all frames (for highlighting nodes). */
export function namesByReference(frames = []) {
  const names = new Map();
  frames.forEach((frame) => (frame.variables ?? []).forEach((variable) => {
    if (!variable.referenceId) return;
    const list = names.get(variable.referenceId) ?? [];
    if (!list.includes(variable.name)) list.push(variable.name);
    names.set(variable.referenceId, list);
  }));
  return names;
}
