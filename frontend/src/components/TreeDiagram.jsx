import { layoutTree } from '../treeModel.js';

const COLUMN = 46;   // minimum horizontal distance between in-order positions
const LEVEL = 58;    // vertical distance between depths
const NODE = 34;     // node height (and width of a circle)

/** Short labels fit a circle; longer ones (e.g. `key : value`) widen the node into a pill. */
const nodeWidth = (label) => Math.max(NODE, Math.min(132, 12 + 6.4 * String(label).length));

/**
 * Draws a binary tree the way it is drawn on a whiteboard: root on top, children below, connected
 * by lines (SVG). Nodes pointed to by current variables are highlighted with the variable names.
 */
export default function TreeDiagram({ nodes, rootId, highlights = new Map(), caption, onNodeFocus }) {
  const { placed, edges, columns, depth, truncated } = layoutTree(nodes, rootId);
  if (placed.length === 0) return null;
  const column = Math.max(COLUMN, ...placed.map(({ node }) => nodeWidth(node.label) + 10));
  const width = Math.max(columns * column, NODE + 12);
  const height = (depth + 1) * LEVEL;
  const cx = (x) => x * column + column / 2;
  const cy = (y) => y * LEVEL + NODE / 2 + 4;
  return <div className="tree-block">
    {caption && <div className="collection-head">{caption}</div>}
    <div className="tree-scroll">
      <div className="tree-diagram" style={{ width, height }} role="tree" aria-label={`Tree with ${placed.length} nodes`}>
        <svg className="tree-edges" width={width} height={height} aria-hidden="true">
          {edges.map((edge) => <line key={`${edge.from.node.id}-${edge.to.node.id}`}
            x1={cx(edge.from.x)} y1={cy(edge.from.y) + NODE / 2 - 2} x2={cx(edge.to.x)} y2={cy(edge.to.y) - NODE / 2 + 2} />)}
        </svg>
        {placed.map(({ node, x, y }) => {
          const names = highlights.get(node.id) ?? [];
          const w = nodeWidth(node.label);
          return <div key={node.id} role="treeitem" aria-label={`${node.label}${names.length ? `, ${names.join(', ')}` : ''}`}
            className={`tree-node ${node.red ? 'tree-node-red' : ''} ${names.length ? 'tree-node-current' : ''}`}
            style={{ left: cx(x) - w / 2, top: cy(y) - NODE / 2, width: w }}
            title={`${node.label} · #${node.id}`}
            onDoubleClick={onNodeFocus ? () => onNodeFocus(node.id) : undefined}>
            <span className="tree-node-value">{node.label}</span>
            {names.length > 0 && <span className="tree-node-names">{names.join(', ')}</span>}
          </div>;
        })}
      </div>
    </div>
    {truncated && <small className="collection-note">Large tree: only the first 200 nodes are drawn.</small>}
  </div>;
}
