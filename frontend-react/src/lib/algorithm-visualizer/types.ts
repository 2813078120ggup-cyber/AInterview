export type AlgorithmStepType =
  | 'start'
  | 'compare'
  | 'swap'
  | 'visit'
  | 'enqueue'
  | 'move'
  | 'complete'

export type AlgorithmStepBase = {
  line: number
  type: AlgorithmStepType
  description: string
}

export type ArrayAlgorithmStep = AlgorithmStepBase & {
  kind: 'array'
  data: number[]
  activeIndices?: number[]
  sortedIndices?: number[]
  left?: number
  right?: number
  mid?: number
  target?: number
  foundIndex?: number
}

export type LinkedListNode = {
  id: string
  value: number
  nextId: string | null
}

export type LinkedListAlgorithmStep = AlgorithmStepBase & {
  kind: 'linked-list'
  nodes: LinkedListNode[]
  headId: string | null
  currentId: string | null
  previousId: string | null
  nextId: string | null
}

export type GraphNode = {
  id: string
  label: string
  x: number
  y: number
}

export type GraphEdge = {
  from: string
  to: string
}

export type GraphAlgorithmStep = AlgorithmStepBase & {
  kind: 'graph'
  nodes: GraphNode[]
  edges: GraphEdge[]
  visited: string[]
  activeNode: string | null
  frontier: string[]
  traversal: string[]
}

export type AlgorithmStep = ArrayAlgorithmStep | LinkedListAlgorithmStep | GraphAlgorithmStep

export type VisualizerKind = AlgorithmStep['kind']

export type VisualizerAlgorithm = {
  slug: string
  title: string
  englishTitle: string
  category: string
  kind: VisualizerKind
  difficulty: '入门' | '基础' | '进阶'
  timeComplexity: string
  spaceComplexity: string
  description: string
  code: string[]
  defaultInput: number[]
  defaultTarget?: number
  buildSteps: (input: number[], target?: number) => AlgorithmStep[]
}
