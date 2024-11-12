import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Represents an instruction with its properties
class Instruction {
    enum Type { ALU, LOAD, STORE, BRANCH, NOP }

    int id;
    Type type;
    String opcode;
    String src1;
    String src2;
    String dest;
    int pc;
    int predictedPC; // For branch instructions

    public Instruction(int id, Type type, String opcode, String src1, String src2, String dest, int pc, int predictedPC) {
        this.id = id;
        this.type = type;
        this.opcode = opcode;
        this.src1 = src1;
        this.src2 = src2;
        this.dest = dest;
        this.pc = pc;
        this.predictedPC = predictedPC;
    }

    @Override
    public String toString() {
        return String.format("Instr%d(%s)", id, opcode);
    }
}

// Handles register renaming to eliminate false dependencies
class RegisterRenamer {
    private Map<String, String> renameTable; // Architectural -> Physical
    private Queue<String> freeList;

    public RegisterRenamer() {
        renameTable = new HashMap<>();
        freeList = new LinkedList<>();
        // Initialize rename table (R0-R31 mapped to P0-P31)
        for (int i = 0; i < 32; i++) {
            renameTable.put("R" + i, "P" + i);
        }
        // Initialize free list (P32-P127)
        for (int i = 32; i < 128; i++) {
            freeList.add("P" + i);
        }
    }

    // Rename destination register
    public String rename(String dest) throws Exception {
        if (freeList.isEmpty()) {
            throw new Exception("No free physical registers available!");
        }
        String newPhys = freeList.poll();
        renameTable.put(dest, newPhys);
        return newPhys;
    }

    // Free a physical register
    public void free(String phys) {
        freeList.add(phys);
    }

    // Get current physical mapping
    public String getPhysical(String arch) {
        return renameTable.getOrDefault(arch, arch);
    }

    // Get architectural register from physical register
    public String getArchRegister(String phys) {
        for (Map.Entry<String, String> e : renameTable.entrySet()) {
            if (e.getValue().equals(phys)) {
                return e.getKey();
            }
        }
        return phys; // If not found, return as is
    }

    @Override
    public String toString() {
        return renameTable.toString();
    }
}

// Manages the issue queue
class IssueQueue {
    private int capacity;
    private List<Instruction> queue;

    public IssueQueue(int capacity) {
        this.capacity = capacity;
        this.queue = new ArrayList<>();
    }

    public boolean add(Instruction instr) {
        if (queue.size() < capacity) {
            queue.add(instr);
            System.out.println("Added to Issue Queue: " + instr);
            return true;
        }
        return false;
    }

    // Simple FIFO issue policy; can be enhanced with more sophisticated policies
    public Instruction issue() {
        if (queue.isEmpty()) return null;
        Instruction instr = queue.remove(0);
        System.out.println("Issued from Issue Queue: " + instr);
        return instr;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public String toString() {
        return queue.toString();
    }
}

// Represents an entry in the Reorder Buffer
class ROBEntry {
    int id;
    Instruction instr;
    String dest; // Physical register
    Integer value; // Result of execution
    boolean ready;
    boolean exception;

    public ROBEntry(int id, Instruction instr, String dest) {
        this.id = id;
        this.instr = instr;
        this.dest = dest;
        this.value = null;
        this.ready = false;
        this.exception = false;
    }

    @Override
    public String toString() {
        return String.format("ROBEntry%d(%s, Dest=%s, Ready=%s)", id, instr, dest, ready);
    }
}

// Manages the Reorder Buffer
class ReorderBuffer {
    private int capacity;
    private Queue<ROBEntry> buffer;
    private int nextId;

    public ReorderBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new LinkedList<>();
        this.nextId = 1;
    }

    public ROBEntry add(Instruction instr, String dest) throws Exception {
        if (buffer.size() >= capacity) {
            throw new Exception("Reorder Buffer is full!");
        }
        ROBEntry entry = new ROBEntry(nextId++, instr, dest);
        buffer.add(entry);
        System.out.println("Added to ROB: " + entry);
        return entry;
    }

    public ROBEntry peek() {
        return buffer.peek();
    }

    public ROBEntry commit() {
        return buffer.poll();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    // Provide access to ROB entries for searching
    public List<ROBEntry> getEntries() {
        return new ArrayList<>(buffer);
    }

    @Override
    public String toString() {
        return buffer.toString();
    }
}

// Simple Branch Predictor using a Branch History Table
class BranchPredictor {
    private Map<Integer, Boolean> bht; // PC -> Prediction

    public BranchPredictor() {
        bht = new HashMap<>();
    }

    // Predict branch: true = taken, false = not taken
    public boolean predict(int pc) {
        return bht.getOrDefault(pc, false); // Default not taken
    }

    // Update predictor with actual outcome
    public void update(int pc, boolean taken) {
        bht.put(pc, taken);
        System.out.println("Branch Predictor updated: PC=" + pc + ", Taken=" + taken);
    }
}

// Simulates an execution unit (e.g., ALU)
class ExecutionUnit {
    private String name;
    private boolean busy;
    private Instruction currentInstr;
    private ROBEntry robEntry;
    private int cyclesRemaining;
    private RegisterRenamer renamer;
    private Map<String, Integer> registerFile;
    private MemorySystem memorySystem;

    public ExecutionUnit(String name, RegisterRenamer renamer, Map<String, Integer> registerFile, MemorySystem memorySystem) {
        this.name = name;
        this.busy = false;
        this.currentInstr = null;
        this.robEntry = null;
        this.cyclesRemaining = 0;
        this.renamer = renamer;
        this.registerFile = registerFile;
        this.memorySystem = memorySystem;
    }

    // Issue instruction to this execution unit
    public boolean issue(Instruction instr, ROBEntry robEntry) {
        if (busy) return false;
        this.currentInstr = instr;
        this.robEntry = robEntry;
        this.busy = true;
        // Assign execution latency based on opcode
        switch (instr.opcode) {
            case "ADD":
            case "SUB":
                this.cyclesRemaining = 1;
                break;
            // Add more cases for different opcodes if needed
            default:
                this.cyclesRemaining = 1;
        }
        System.out.println(name + " started executing " + instr);
        return true;
    }

    // Execute a cycle
    public void executeCycle() {
        if (!busy) return;
        cyclesRemaining--;
        if (cyclesRemaining <= 0) {
            // Execution complete
            performOperation();
            busy = false;
            currentInstr = null;
            robEntry = null;
        }
    }

    // Perform the actual operation based on opcode
    private void performOperation() {
        String src1Phys = renamer.getPhysical(currentInstr.src1);
        String src2Phys = currentInstr.src2 != null ? renamer.getPhysical(currentInstr.src2) : null;

        int operand1 = registerFile.getOrDefault(currentInstr.src1, 0);
        int operand2 = currentInstr.src2 != null ? registerFile.getOrDefault(currentInstr.src2, 0) : 0;
        int result = 0;

        switch (currentInstr.opcode) {
            case "ADD":
                result = operand1 + operand2;
                break;
            case "SUB":
                result = operand1 - operand2;
                break;
            // Add more cases for different opcodes if needed
            default:
                System.err.println("Unsupported ALU opcode: " + currentInstr.opcode);
        }

        robEntry.value = result;
        robEntry.ready = true;
        System.out.println(name + " completed executing " + currentInstr + ", Result=" + robEntry.value);
    }

    public boolean isBusy() {
        return busy;
    }

    @Override
    public String toString() {
        if (busy) {
            return name + " executing " + currentInstr;
        }
        return name + " idle";
    }
}

// Handles parallel memory accesses
class MemorySystem {
    private Queue<Instruction> loadQueue;
    private Queue<Instruction> storeQueue;
    private int loadCapacity;
    private int storeCapacity;
    private ReorderBuffer rob; // Reference to ReorderBuffer
    private RegisterRenamer renamer; // Reference to RegisterRenamer
    private Map<Integer, Integer> memory; // Simple memory model

    public MemorySystem(int loadCapacity, int storeCapacity, ReorderBuffer rob, RegisterRenamer renamer) {
        loadQueue = new LinkedList<>();
        storeQueue = new LinkedList<>();
        this.loadCapacity = loadCapacity;
        this.storeCapacity = storeCapacity;
        this.rob = rob;
        this.renamer = renamer;
        memory = new HashMap<>();
        // Initialize some memory addresses with predefined values
        memory.put(100, 72);
        memory.put(0, 0);
    }

    public boolean addLoad(Instruction instr) {
        if (loadQueue.size() < loadCapacity) {
            loadQueue.add(instr);
            System.out.println("Added to Load Queue: " + instr);
            return true;
        }
        return false;
    }

    public boolean addStore(Instruction instr) {
        if (storeQueue.size() < storeCapacity) {
            storeQueue.add(instr);
            System.out.println("Added to Store Queue: " + instr);
            return true;
        }
        return false;
    }

    // Execute a cycle of memory operations
    public void executeCycle() {
        // Process one load and one store per cycle
        if (!loadQueue.isEmpty()) {
            Instruction load = loadQueue.poll();
            System.out.println("Executed Load: " + load);
            // Find the corresponding ROB entry and mark it as ready
            ROBEntry robEntry = findROBEntry(load.id);
            if (robEntry != null) {
                // Simulate memory read
                int address = registerFile.getOrDefault(load.src1, 0);
                int loadedValue = memory.getOrDefault(address, 0);
                robEntry.value = loadedValue;
                robEntry.ready = true;
                System.out.println("Load Instruction " + load.id + " is ready with value " + robEntry.value);
            } else {
                System.err.println("ROB Entry not found for Load Instruction " + load.id);
            }
        }
        if (!storeQueue.isEmpty()) {
            Instruction store = storeQueue.poll();
            System.out.println("Executed Store: " + store);
            // Find the corresponding ROB entry and mark it as ready
            ROBEntry robEntry = findROBEntry(store.id);
            if (robEntry != null) {
                // Simulate memory write
                int address = registerFile.getOrDefault(store.src1, 0);
                int valueToStore = registerFile.getOrDefault(store.src2, 0);
                memory.put(address, valueToStore);
                robEntry.ready = true;
                System.out.println("Store Instruction " + store.id + " is ready.");
            } else {
                System.err.println("ROB Entry not found for Store Instruction " + store.id);
            }
        }
    }

    // Helper method to find the ROBEntry by instruction ID
    private ROBEntry findROBEntry(int instrId) {
        for (ROBEntry entry : rob.getEntries()) {
            if (entry.instr.id == instrId) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "LoadQueue: " + loadQueue + ", StoreQueue: " + storeQueue;
    }

    // Reference to Register File for memory operations
    private Map<String, Integer> registerFile;

    public void setRegisterFile(Map<String, Integer> registerFile) {
        this.registerFile = registerFile;
    }
}

// The main pipeline simulator integrating all components
public class PipelineSimulator {
    // Components
    private List<Instruction> program;
    private int pc;
    private RegisterRenamer renamer;
    private IssueQueue issueQueue;
    private ReorderBuffer rob;
    private BranchPredictor branchPredictor;
    private List<ExecutionUnit> executionUnits;
    private MemorySystem memorySystem;
    private Map<String, Integer> registerFile; // Architectural registers

    // Simulation parameters
    private int cycle;

    public PipelineSimulator(List<Instruction> program) {
        this.program = program;
        this.pc = 0;
        this.renamer = new RegisterRenamer();
        this.issueQueue = new IssueQueue(16); // Example capacity
        this.rob = new ReorderBuffer(32); // Example capacity
        this.branchPredictor = new BranchPredictor();
        this.registerFile = new HashMap<>();
        // Initialize register file with meaningful values
        initializeRegisterFile();
        // Initialize MemorySystem
        this.memorySystem = new MemorySystem(2, 2, rob, renamer);
        // Set the Register File reference in MemorySystem
        this.memorySystem.setRegisterFile(registerFile);
        // Initialize Execution Units with references to Register File and Memory System
        this.executionUnits = Arrays.asList(new ExecutionUnit("ALU1", renamer, registerFile, memorySystem),
                                           new ExecutionUnit("ALU2", renamer, registerFile, memorySystem));
        this.cycle = 0;
    }

    // Initialize register file with predefined values for meaningful execution
    private void initializeRegisterFile() {
        // For example:
        registerFile.put("R1", 10);
        registerFile.put("R2", 16);
        registerFile.put("R4", 100);
        registerFile.put("R5", 50);
        registerFile.put("R6", 0);
        registerFile.put("R7", 5);
        registerFile.put("R8", 5);
        // Initialize remaining registers to 0
        for (int i = 0; i < 32; i++) {
            String reg = "R" + i;
            registerFile.putIfAbsent(reg, 0);
        }
    }

    // Fetch stage
    private void fetch() {
        if (pc >= program.size()) {
            System.out.println("Cycle " + cycle + ": Fetch completed. No more instructions to fetch.");
            return; // No more instructions
        }

        Instruction instr = program.get(pc);
        System.out.println("Cycle " + cycle + ": Fetching " + instr + " at PC=" + pc);

        // Handle branch prediction
        if (instr.type == Instruction.Type.BRANCH) {
            boolean prediction = branchPredictor.predict(instr.pc);
            if (prediction) {
                pc = instr.predictedPC;
                System.out.println("Branch predicted TAKEN. New PC=" + pc);
            } else {
                pc++;
                System.out.println("Branch predicted NOT TAKEN. PC incremented to " + pc);
            }
        } else {
            pc++;
        }

        // Decode and rename
        try {
            decodeAndRename(instr);
        } catch (Exception e) {
            System.err.println("Decode/Rename Error: " + e.getMessage());
        }
    }

    // Decode and rename stage
    private void decodeAndRename(Instruction instr) throws Exception {
        String renamedDest = null;
        if (instr.dest != null) {
            renamedDest = renamer.rename(instr.dest);
        }
        // Add to ROB
        ROBEntry robEntry = rob.add(instr, renamedDest);
        // Add to Issue Queue
        if (!issueQueue.add(instr)) {
            System.err.println("Issue Queue is full. Stalling...");
            // Handle stall (for simplicity, do nothing here)
        }
    }

    // Issue stage
    private void issue() {
        if (issueQueue.isEmpty()) return;
        Instruction instr = issueQueue.issue();
        if (instr == null) return;

        // Find ROB entry
        ROBEntry robEntry = getROBEntryByInstructionId(instr.id);
        if (robEntry == null) {
            System.err.println("ROB Entry not found for " + instr);
            return;
        }

        // Issue to appropriate execution unit or memory system
        switch (instr.type) {
            case ALU:
                boolean issued = false;
                for (ExecutionUnit eu : executionUnits) {
                    if (!eu.isBusy()) {
                        eu.issue(instr, robEntry);
                        issued = true;
                        break;
                    }
                }
                if (!issued) {
                    System.err.println("No available Execution Units. Stalling...");
                    issueQueue.add(instr); // Re-add to issue queue
                }
                break;
            case LOAD:
                if (!memorySystem.addLoad(instr)) {
                    System.err.println("Load Queue is full. Stalling...");
                    issueQueue.add(instr);
                }
                break;
            case STORE:
                if (!memorySystem.addStore(instr)) {
                    System.err.println("Store Queue is full. Stalling...");
                    issueQueue.add(instr);
                }
                break;
            case BRANCH:
                // Handle branch outcome and mark ROB entry as ready
                boolean taken = registerFile.getOrDefault(instr.src1, 0) == registerFile.getOrDefault(instr.src2, 0);
                branchPredictor.update(instr.pc, taken);
                boolean mispredicted = branchPredictor.predict(instr.pc) != taken;
                if (mispredicted) {
                    // Misprediction: Flush pipeline and set PC correctly
                    System.out.println("Branch Misprediction detected. Flushing pipeline.");
                    // Reset PC to actual outcome
                    pc = taken ? instr.predictedPC : instr.pc + 1;
                    // Clear Issue Queue and ROB
                    issueQueue = new IssueQueue(16);
                    rob = new ReorderBuffer(32);
                    // Reinitialize MemorySystem with new ROB and Renamer
                    memorySystem = new MemorySystem(2, 2, rob, renamer);
                    // Update Execution Units with new MemorySystem and Register File
                    executionUnits = Arrays.asList(new ExecutionUnit("ALU1", renamer, registerFile, memorySystem),
                                                   new ExecutionUnit("ALU2", renamer, registerFile, memorySystem));
                } else {
                    // Correct prediction: mark ready
                    robEntry.ready = true;
                    System.out.println("Branch Instruction " + instr.id + " is ready.");
                }
                break;
            case NOP:
                // Mark NOP as ready
                robEntry.ready = true;
                System.out.println("NOP Instruction " + instr.id + " is ready.");
                break;
        }
    }

    // Helper method to retrieve ROBEntry by instruction ID
    private ROBEntry getROBEntryByInstructionId(int instrId) {
        for (ROBEntry entry : rob.getEntries()) {
            if (entry.instr.id == instrId) {
                return entry;
            }
        }
        return null;
    }

    // Execute stage
    private void execute() {
        for (ExecutionUnit eu : executionUnits) {
            eu.executeCycle();
        }
        // Execute memory operations
        memorySystem.executeCycle();
    }

    // Commit stage
    private void commit() {
        while (!rob.isEmpty()) {
            ROBEntry entry = rob.peek();
            if (entry.ready) {
                rob.commit();
                if (entry.dest != null) {
                    // Update register file
                    String archReg = renamer.getArchRegister(entry.dest);
                    registerFile.put(archReg, entry.value);
                    // Free the physical register
                    renamer.free(entry.dest);
                    System.out.println("Committed " + entry + " to Register File (Arch " + archReg + " = " + entry.value + ").");
                } else {
                    // For STORE and BRANCH instructions, no register update is needed
                    System.out.println("Committed " + entry + " to Register File (no update).");
                }
            } else {
                // Cannot commit further instructions as the next one is not ready
                break;
            }
        }
    }

    // Run the simulation for a specified number of cycles
    public void run(int maxCycles) {
        while (cycle < maxCycles) {
            System.out.println("\n=== Cycle " + cycle + " ===");
            // Commit
            commit();
            // Execute
            execute();
            // Issue
            issue();
            // Fetch
            fetch();
            // Increment cycle
            cycle++;
            // Terminate if program is done
            if (pc >= program.size() && issueQueue.isEmpty() && rob.isEmpty()) {
                System.out.println("Program completed.");
                break;
            }
        }
        printState();
    }

    // Print the current state of the pipeline
    private void printState() {
        System.out.println("\n=== Final State ===");
        System.out.println("Register File: " + registerFile);
        System.out.println("Rename Table: " + renamer);
        System.out.println("Issue Queue: " + issueQueue);
        System.out.println("Reorder Buffer: " + rob);
        System.out.println("Execution Units:");
        for (ExecutionUnit eu : executionUnits) {
            System.out.println("  " + eu);
        }
        System.out.println("Memory System: " + memorySystem);
    }

    // Main method to execute the simulator
    public static void main(String[] args) {
        // Define a sample program
        List<Instruction> program = new ArrayList<>();
        program.add(new Instruction(1, Instruction.Type.ALU, "ADD", "R1", "R2", "R3", 0, 0));
        program.add(new Instruction(2, Instruction.Type.LOAD, "LD", "R4", null, "R5", 4, 0));
        program.add(new Instruction(3, Instruction.Type.STORE, "ST", "R5", "R6", null, 8, 0));
        program.add(new Instruction(4, Instruction.Type.BRANCH, "BEQ", "R7", "R8", null, 12, 16));
        program.add(new Instruction(5, Instruction.Type.ALU, "SUB", "R3", "R5", "R9", 16, 0));
        program.add(new Instruction(6, Instruction.Type.NOP, "NOP", null, null, null, 20, 0));

        // Create simulator instance
        PipelineSimulator simulator = new PipelineSimulator(program);

        // Run simulator for a maximum of 20 cycles
        simulator.run(20);
    }
}
