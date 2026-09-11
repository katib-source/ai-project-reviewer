import { MotionConfig, motion } from 'motion/react'

export interface ImportOutcome {
  kind: 'success' | 'failure'
  title: string
  detail: string
}

interface ImportResultProps {
  outcome: ImportOutcome
  onRetry: () => void
}

const draw = (delay: number) => ({
  initial: { pathLength: 0, opacity: 0 },
  animate: { pathLength: 1, opacity: 1 },
  transition: { pathLength: { delay, duration: 0.45, ease: 'easeOut' as const }, opacity: { delay, duration: 0.01 } },
})

/**
 * Animated result of a project import, shown over the import dialog: a checkmark that draws
 * itself on success, a cross with a short shake on failure. Honours the user's reduced-motion
 * setting.
 */
export function ImportResult({ outcome, onRetry }: ImportResultProps) {
  const success = outcome.kind === 'success'
  return (
    <MotionConfig reducedMotion="user">
      <motion.div
        animate={{ opacity: 1 }}
        className={`import-result ${outcome.kind}`}
        initial={{ opacity: 0 }}
        role={success ? 'status' : 'alert'}
      >
        <motion.svg
          animate={success ? { scale: 1 } : { scale: 1, x: [0, -9, 9, -6, 6, 0] }}
          aria-hidden="true"
          height="84"
          initial={{ scale: 0.6 }}
          transition={success
            ? { type: 'spring', stiffness: 260, damping: 16 }
            : { scale: { type: 'spring', stiffness: 260, damping: 16 }, x: { delay: 0.75, duration: 0.45 } }}
          viewBox="0 0 84 84"
          width="84"
        >
          <motion.circle cx="42" cy="42" fill="none" r="36" strokeWidth="5" {...draw(0)} />
          {success
            ? <motion.path d="M27 43 L38 54 L58 32" fill="none" strokeLinecap="round" strokeLinejoin="round" strokeWidth="6" {...draw(0.35)} />
            : <>
                <motion.path d="M30 30 L54 54" fill="none" strokeLinecap="round" strokeWidth="6" {...draw(0.35)} />
                <motion.path d="M54 30 L30 54" fill="none" strokeLinecap="round" strokeWidth="6" {...draw(0.5)} />
              </>}
        </motion.svg>
        <motion.div animate={{ opacity: 1, y: 0 }} initial={{ opacity: 0, y: 8 }} transition={{ delay: 0.45, duration: 0.3 }}>
          <strong>{outcome.title}</strong>
          <p>{outcome.detail}</p>
          {!success && <button className="secondary-button" onClick={onRetry} type="button">Try again</button>}
        </motion.div>
      </motion.div>
    </MotionConfig>
  )
}
